---
layout: post
title: "Exploring the design of clojure.tools.namespace"
date: 2018-01-31
comments: true
categories: blog
tags: clojure software explanation
---

`tools.namespace` is a utility library I use for Clojure projects. It helps
Clojurists work effectively with namespaces and dependencies between them. I
recently filed an issue TNS-48 [^1] to add support for adding non-Clojure files
as dependencies.

Stuart replied:

> I have been aware of the value of this feature for a long time now, but I
> expect it would require significant, breaking changes to the internal data
> structures used by tools.namespace.

This piqued my curiosity to understand the implementation of `tools.namespace`
to see what is involved. From an outsider's perspective, the library does three
things.

1. Finds modified files since the last call to refresh, and to find the
   corresponding namespaces
2. Determine which namespaces to unload and reload
3. Performs the unload & load operations

The rest of the post will look at tools.namespace, with this in mind:

1. The data structures needed to track the above tasks
2. How does `clojure.tools.namespace.repl/refresh` clean up namespaces.
3. How might tracking external files be implemented?
4. Closing Thoughts

## Data Structures of tools.namespace

`tools.namespace` stores some state, also known as the `tracker`, a Clojure map.
Conveniently, the source documents the map keys. I copied it verbatim below.
[^2]

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

Given these pieces of data, we can guess at how the high-level operations may be
implemented.

Finding new and modified files uses file modification time, comparing it to
`:clojure.tools.namespace.dir/time`. Deleted files can be identified with a
set-difference of the current file list & `:clojure.tools.namespace.dir/files`.

The `filemap` maps `files` to `namespaces` to find namespaces to load & unload.

Two lists track pending operations to execute after the tracker state update.
Removed and modified namespaces and all dependent namespaces are stored in
`:clojure.tools.namespace.track/unload`. Added and modified namespaces and all
dependent namespaces are stored in `:clojure.tools.namespace.track/load`.
Observe that modified files are in both lists. Why? Unloading the namespaces
cleans up vars that are no longer defined in the source code.

I found it interesting that the tracker serves both purposes of maintaining the
current state and pending load/unload operations.

## Invoking `refresh!`

Users of the library interact with the namespace `clojure.tools.namespace.repl`,
using `refresh` or `refresh-all!`. Both delegate to a function `do-refresh`,
which does the following in order.

1. `dir/scan-dirs`: Update the tracker state since the last run
2. Removes excluded namespaces from the tracker
3. `reload/track-reload`: Unloads & Loads namespaces using the tracker's
   `unload` and `load` lists
4. Optionally invokes function `:after-sym` provided by user

The interesting bits are in `dir/scan-dirs` and `reload/track-reload`.

## Inside `dir/scan-dirs`

scan-dirs updates the state of the tracker. It uses `clojure.tools.classpath` to
find files in the project ending in `.clj`. This produces two file lists:
modified files and deleted files. These files are mapped to namespaces, and then
`tools.namespace` tries to figure out which order to unload & load namespaces.

### Determining namespace unload & load

`tools.namespace` orders unload & load by performing a topographic sort by
namespace dependencies. This is a neat little library and I'd love to
investigate it more in the future.

In the unload operation, the namespaces are ordered such that dependent
namespaces are unloaded first. I'm unsure why unloading needs to be ordered.
From skimming the implementation, it shouldn't matter.

In the load operation, ordering *does matter*. This is documented in the
[README.md#reloading-code-motivation](https://github.com/clojure/tools.namespace#reloading-code-motivation).
`tools.namespace` comes with a small graph API to figure out the load order to
avoid all the gotchas. `tools.namespace` uses `(require your.name :reload)`
which would re-define protocols & multi-methods if dependent namespaces are
loaded first.

Un-ordered reloading of namespaces (i.e. humans on the REPL) can create
frustrating exceptions, especially for beginners. See below for an example.

``` clojure
;; file my/ns/a.clj
(ns my.ns.a)

(defprotocol MyProtocol
  (say [_] ..))

;; file my/ns/b.clj
(ns my.ns.b 
   (require [my.ns.a :as a]))

(def duck (reify a/MyProtocol
   (say [_] "quack")))
   
;; re-evaluating the namespaces in an arbitrary order
(require '[my.ns.b :as b] :reload)
(require '[my.ns.a :as a] :reload)

(a/say b/duck)   ; throws Unhandled java.lang.IllegalArgumentException: No implementation of method!
```

## Unloading namespaces
Each namespace in the `:unload` list is removed with
`clojure.tools.namespace.reload/remove-lib`. This is the referenced code [^3]

``` clojure
(defn remove-lib
  "Remove lib's namespace and remove lib from the set of loaded libs."
  [lib]
  (remove-ns lib)
  (dosync (alter @#'clojure.core/*loaded-libs* disj lib)))
```

The first function `remove-ns` actually removes the namespace, which from
looking at the code modifies the Namespace's concurrent hash-map. The change to
`*loaded-libs*` is peculiar though. From skimming clojure.core, `*loaded-libs*`
is used to provide some locks against concurrently loading libraries. It's used
as a cache to decide whether a namespace should be reloaded from the file.

Modifying `*loaded-libs*` does not appear necessary because `(require [my.lib]
:reload)` will skip checking the `*loaded-libs*` cache. There is *one* case
where not doing so would cause grief. In this example below, we try using
`remove-ns` directly. If you try to work interactively later, you will encounter
yet another frustrating exception.

``` clojure
(require 'my.testing.core)    ; require the library because I need it!
(remove-ns 'my.testing.core') ; remove the library because I no longer need it!

(require 'my.testing.core)    ; at a later time, I want to use the library again
my.testing.core/some-var      ; BOOM! class my.testing.core does not exist!
```

To get around this, you could choose two options to get past the exception:
modify `*loaded-libs*`, or use `(require 'my.testing.core :reload)`.
`tools.namespace` does both!

I was curious why `*loaded-libs*` needs to exist at all. I've asked in the
#clojurians slack and the response I got back was that this is an old piece of
code that just exists. I'd love to hear a follow up from the core Clojure
developers to uncover the mystery of `*loaded-libs*`.

UPDATE (2018/02/04): I found a
[blog](https://www.deepbluelambda.org/programming/clojure/how-clojure-works-namespace-metadata)
with an answer for the mystery of `*loaded-libs*`. When using `require` with `:reload-all`, i.e. `(require my.ns
:reload-all)`, `*loaded-libs*` ensures dependencies are only loaded once.

## How might tracking external files be implemented?

Going back to TNS-48 [^1]. Why can't we have support for arbitrary files? From
inspecting `dir/scan-dirs`, tracking dependencies rely on analyzing files with a
`ns` form. This is tricky for arbitrary files because the tracker works with
files, namespaces, and namespace-dependencies. Arbitrary files have neither a
namespace nor Clojure dependencies. There are 3 improvements needed to support
arbitrary files:

- how to specify dependencies on external files
- how to track changes to external files (an idea: a second pass on scanning
  source files and tracking a map of namespace -> External File)
- how to incorporate external files .modifiedAt timestamps into
  `clojure.tools.namespace.dir/modified-files`

I believe most effort lies in (2).

## Closing Thoughts

I have a lot of respect for `tools.namespace`. After doing this dive, I realize
there are many pitfalls that novices, intermediates and experienced Clojurists
avoid by not reload dependencies by hand. Thank you @stuartsierra for creating
this indispensable tool.

As a Clojurist, the value-oriented nature of Clojure was fully utilized. The
tracker's state is implemented with persistent maps & sets. The tracker's
`filemap` uses `java.io.File` as keys to `clojure.lang.Symbol`, expressing the
relationship of File to namespace. This is only feasible when the objects have a
strong sense of value. (Surprise: `java.io.File` objects have value semantics!).

> It is better to have 100 functions operate on one data structure than 10
> functions on 10 data structures. —Alan Perlis

Lastly, the use of Clojure's immutable collections was refreshing compared to
languages where one tends to program to interfaces. An intermediate Clojurist
should be able to follow the design for two reasons: the collections API is
engrained in Clojurists, and working with collections over abstract interfaces.

I definitely recommend intermediate Clojurists to dive into this library. It is
not arcane magic. One thing I would have liked is for the library to switch from
Maven to Leiningen, to make it easier to integrate with Emacs+CIDER. I ended up
creating a temporary project.clj to match my usual Clojure workflow.


[^1]: See [TNS-48](https://dev.clojure.org/jira/browse/TNS-48).

[^2]: The comments come from the
    [code](https://github.com/clojure/tools.namespace/blob/bb9d7a1e98cc5a1ff53107966c96af6886eb0f5b/src/main/clojure/clojure/tools/namespace/track.cljc#L116-L147).

[^3]: Reload implementation. [link](https://github.com/clojure/tools.namespace/blob/bb9d7a1e98cc5a1ff53107966c96af6886eb0f5b/src/main/clojure/clojure/tools/namespace/reload.clj#L15-L19)
    
