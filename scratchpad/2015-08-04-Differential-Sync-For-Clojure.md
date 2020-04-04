---
layout: post
title: "Ditching REST APIs for Differential Sync"
date: 2015-08-04
comments: true
categories: clojure
tags: clojure, core.async, differential sync
---

From my day jobs, I have become disillusioned with consuming REST APIs to build
rich single page applications. In particular, when building a backend this way,
scaffolding in (backend programming stack of choice) is required when all we
really want is to simply have access to the rich network of data allowed in a
flexible query language. I did not want to build any more REST APIs. They are
boilerplatey, inflexible and waste development cycles.


At the same time, I saw [David Nolen](http://swannodette.github.io) working on
interesting ideas for UI Programming. I am fond of the ideas introduced in
[Om](https://github.com/omcljs/om) where UI programming can be simple by putting
everything that should render inside a single application state. [^2]


After watching David Nolen demonstrate the type of cool things enabled by
Clojure Data structures, I thought I'd take a stab at implementing something in
similar vein, but instead of targeting *JavaScript & Dom*, I wanted to target
*client & server* to rid myself of needing REST APIs.


With this, I built a simple proof of concept library to see if this idea is
feasible or not. I wrote a library called [entangle][entangle], a Clojure +
ClojureScript implementation of Neil Fraser's
[Differential Synchronization][diff-sync] algorithm. [^3] Conceptually,
Differential Synchronization works similar to how Git version control works,
you:

- work on a feature branch
- rebase your changes, and
- then push to master

Unlike Git, you don't work with flat documents. In [Entangle][entangle], you
work with data structures stored in an atom. When an atom's value changes, a
diff gets created and placed onto a *core.async* channel. [^1] This channel can
be piped into another atom, which can be local or over websockets. Taking this
idea a little further, you can have multiple clients __entangled__ to a single
atom to do real time collaborative editing of atoms.


With this, we are free from having to write multiple HTTP endpoints to describe
your application. Write to a local atom, and believe it's state will be propagated over *core.async*.


As a proof of concept, I've built a little web application where a single *atom* on the server is shared with multiple clients. These clients can collaboratively update the atom's content in real time. Check it out [here][entangle-http].

[^1]: `core.async` was a wonderful fit for exploring Differential Sync because it allowed me to write a single implementation of concurrent background processes that worked on both the JVM and web browser.

[^2]: As a note, If you share my views of the deficiency in how applications currently consume REST APIs, check out this recent [talk][om-next] by David Nolen which describes other ways of architecting your application.

[^3]: I cannot take credit for this work. I've mainly implemented what's described in the paper, also building on top of [clj-diff][clj-diff].

[om-next]: https://www.youtube.com/watch?v=ByNs9TG30E8 "Om Next Talk by David Nolen"

[entangle]: http://github.com/rymndhng/entangle "Entangle: Keeping Atoms in Sync"

[diff-sync]: https://neil.fraser.name/writing/sync/ "Differential Sync by Neil fraser"

[clj-diff]: https://github.com/brentonashworth/clj-diff "Clj-Diff"

[entangle-http]: https://www.rymndhng.com/entangle "Entangle Demo App"
