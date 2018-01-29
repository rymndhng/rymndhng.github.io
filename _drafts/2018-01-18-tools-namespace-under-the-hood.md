---
layout: post
title: "tools.namespace under the hood"
date: 2018-01-18
comments: true
categories: clojure patterns explanation
---

=tools.namespace= is a utility library I use for all of my clojure projects. It
helps Clojurists work effectively with multiple files and dependencies between
them. I recently filed an issue TNS-48[^1] to add support for adding non-clojure
files as dependencies. 

Stuart replied:

> I have been aware of the value of this feature for a long time now, but I
> expect it would require significant, breaking changes to the internal data
> structures used by tools.namespace.

This piqued my curiosity to understand the implementation of `tools.namespace`.

The rest of the post will explain these high level operations with three things in mind:

1. The data structures needed to model the above tasks
2. What happens when `clojure.tools.namespace.repl/refresh` is invoked
3. What are the implications of the design

Whenever `tools.namespace` is used to refresh a project, it: 

1. Finds which files have changed since the last call to refresh, and find which namespaces these files are
2. Determine which order to unload and load files
3. Performs the unload & load operations

This is important to keep in mind when discussing the implementation.

## Data Structures of tools.namespace

=tools.namespace= stores a bit of state. This state is known as the `tracker`, a
map. The map's keys are documented in the code. For brevity, I've copy-pasted it
into this post [^3].

``` clojure
(comment
  ;; Structure of the namespace tracker map. Documented for reference
  ;; only: This is not a public API.

  {;; Dependency graph of namespace names (symbols) as defined in
   ;; clojure.tools.namespace.dependency/graph
   :clojure.tools.namespace.track/deps {}

   ;; Ordered list of namespace names (symbols) that need to be
   ;; removed to bring the running system into agreement with the
   ;; source files.
   :clojure.tools.namespace.track/unload ()

   ;; Ordered list of namespace names (symbols) that need to be
   ;; (re)loaded to bring the running system into agreement with the
   ;; source files.
   :clojure.tools.namespace.track/load ()

   ;; Added by clojure.tools.namespace.file: Map from source files
   ;; (java.io.File) to the names (symbols) of namespaces they
   ;; represent.
   :clojure.tools.namespace.file/filemap {}

   ;; Added by clojure.tools.namespace.dir: Set of source files
   ;; (java.io.File) which have been seen by this dependency tracker;
   ;; used to determine when files have been deleted.
   :clojure.tools.namespace.dir/files #{}

   ;; Added by clojure.tools.namespace.dir: Instant when the
   ;; directories were last scanned, as returned by
   ;; System/currentTimeMillis.
   :clojure.tools.namespace.dir/time 1405201862262})
```

Given these pieces of data, we can guess at how each high level operation can be
implemented. Here's a high level explanation of each piece of data:

Finding created, modified and deleted files can be implemented by comparing file
modification time with ``:clojure.tools.namespace.dir/time` and then checking if
the file already exists in `:clojure.tools.namespace.dir/files`.

namespaces to delete are put in the list `:clojure.tools.namespace.track/unload`.

namespaces to create are put in the list `:clojure.tools.namespace.track/load`.

namespaces to update are put in both `:clojure.tools.namespace.track/unload` and
`:clojure.tools.namespace.track/load`. Note: updated namespaces are first
unloaded to clean up unused vars.

Ordering of unload & load operations can be found using a topographic sort of
namespaces by dependencies.

The interesting bit is the tracker stores the current state of the world as well
as commands to execute in order to refresh the state.

## Invoking `refresh!`

Users of the library interact with the namespace `clojure.tools.namespace.repl`, mostly using `refresh` or `refresh-all!`. Both call `do-refresh` to do the heavy lifting. This consists of the following steps:

1. `dir/scan-dirs`: Update the tracker state since last run
2. Removes excluded namespaces from the state
3. `reload/track-reload`: Unloads & Loads namespaces according to the tracker's `::track/unload` and `::track/load` 
4. Optionally invokes function `:after-sym` provided by user

The interesting bits are in `dir/scan-dirs` and `reload/track-reload`.

## Inside `dir/scan-dirs`

This function most of the heavy lifting. It uses `clojure.tools.classpath` to
find files in the project that end in `.clj`. These files are compared against
the list of `files` known to the `tracker` to figure out which namespaces to
load and unload.

The neat part is that most of the house-keeping on tracker is functional. It
makes use of immutability, `clojure.set`, predicates & the collections library.
I/O is done only at the outermost level (reading files & parsing namespace
declarations). 

As a Clojurist I found it easy to understand the internals of the `tracker`
because the state of the tracker used immutable lists & maps. These semantics I
understood very well as each file is `reduced` into the tracker to update. This
surprised me as relative-ly easy to follow. I contrast this with other libraries
in other languages, where one has to think about abstract interfaces where the
implementation is one-step away. It makes me awe at the power of immutable
collections.

> It is better to have 100 functions operate on one data structure than 10
> functions on 10 data structures. —Alan Perlis

## Inside `reload/track-reload`

This is two separate operations: unloading all namespaces, then reloading all namespaces.

The implementation of `remove-lib` does two things. It calls `remove-ns`, which
is expected. It also removes an entry from `clojure.core/*loaded-libs*`. This is
interesting, why? @stuart TODO:

Thoughts;
- it appears *loaded-libs* is used to cache a list of namespaces that a user has already loaded. If it's already in the list, and remove-ns is called, then requiring it again will give the user an empty namespace
- however, because the lib is removed from *loaded-libs*, the :reload seems unnecessary ?thinking cap?

Loading uses the known form: `(require ... :reload)`. This is familiar to us
and tutorials recommend using the :reload form to ensure the namespace re-evaluated properly. Is this necessary if clojure.core/*loaded-libs* 
properly refreshed. 

Looking at the source of load-lib, it appears unnecessary that removing from *loaded-libs* and (require .. :reload) is necessary?  There's a conditional that checks if the library exists *loaded-libs* or has :reload. 

This is mainly to cover the case where a namespace has cleaned up, and you end up manually requiring it later. At that time, you expect the code in that namespace to reload (regardless of tools.namespace is used). If it's not removed from *loaded-libs*, you the user will encounter a load error because the runtime thinks the library is loaded when it isn't.

This would suck and annoy the user, hence it's removed from both *loaded-libs* and remove-ns. Q: why is there a divergence between **loaded-libs* and namespaces?

TODO: look at the implementation of creating a new "namespace" and how that impacts finding symbols.

### Implications

So, the interesting dance of tools.namespace revolves around namespaces. The entire tracker store resolves around clojure files & namespaces. The rules for defining dependencies are tied to namespaces and their dependency form. The tricky part of supporting non-clojure files is two fold: how to identify these files, and how to understand their dependency chain. This is do-able, but requires the implementation to be more abstract.

The other thing is how to think about file-reloading. Namespaces are mutable objects. The interesting thing is two namespaces can share the same name, i.e. old code referencing the old namespace... etc. what if we could remove all the vars from a namespace instead :thinking-face: WHy not remove all the vars from a file?



[^1]: TNS-48 https://dev.clojure.org/jira/browse/TNS-48

[^2]: tools.namespace uses a var, which is a singleton. This is ok since the use of library is mainly for interactive development where there's only you (the developer calling refresh!). See https://github.com/clojure/tools.namespace/blob/bb9d7a1e98cc5a1ff53107966c96af6886eb0f5b/src/main/clojure/clojure/tools/namespace/repl.clj#L17

[^3]: The comments are lifted from the code [here](https://github.com/clojure/tools.namespace/blob/bb9d7a1e98cc5a1ff53107966c96af6886eb0f5b/src/main/clojure/clojure/tools/namespace/track.cljc#L116-L147).
