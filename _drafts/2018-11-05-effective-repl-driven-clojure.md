---
title: Effective REPL-driven Clojure
layout: post
tags: clojure repl workflow
---

Here are some techniques I use to get more mileage out of REPL-driven
experimentation. These techniques work independently of which editor you choose.

# Evaluate forms frequently

In Clojure, the unit of code is a **form**. Re-evaluate forms early and often.
This is analogous to saving files in other programming languages.

I generally prefer evaluating forms to evaluating files.

> **TIP**: Clojure editors have a keybinding to "evaluate the top level form".
> Know this keybinding by heart!

# Start stateful things in REPL before you do anything else

When I start the Clojure REPL on a project, I call a function to start all the
stateful components first. This way, I know my system is in a working state
before I start hacking away and breaking things.

In my projects at work, I always have a function I call `(dev/start)`. Once
these components are started, they are accessible through a *var* so that I can
readily use it at the REPL. Get familiar with where you put your stateful
components (i.e. database, config) so that you can focus on experimenting with
your code.

I recommend using [mount](https://github.com/tolitius/mount) as a starting point. If you prefer using component,
check out [reloaded.repl](https://github.com/weavejester/reloaded.repl). For more background reading on this subject, check out
the [reloaded workflow](http://thinkrelevance.com/blog/2013/06/04/clojure-workflow-reloaded).

# Use comment forms to document your experiments

I seldom type forms directly into the REPL. I prefer writing code in a [comment
form](https://clojuredocs.org/clojure.core/comment). I document my experiments for two reasons:

1.  Repeatable setup for experiments i.e. sample input
2.  Runnable examples for myself and other developers

This serves a different purpose than writing tests. A test is written to check
that a behavior stays the same. A comment invites other developers to learn
through experimentation.

This technique is used inside *clojure.core* as well. See [clojure.set](https://github.com/clojure/clojure/blob/4ef4b1ed7a2e8bb0aaaacfb0942729252c2c3091/src/clj/clojure/set.clj#L158-L176).

# Use def for debugging

I use `def` for debugging gnarlier bits of logic. Generally, I don't have to use
it, but this can help you isolate problematic code without using a debugger or
printing.

There are two techniques that make use of `def`. I've linked to them here:

-   **Using inline def:** [https://blog.michielborkent.nl/2017/05/25/inline-def-debugging/](https://blog.michielborkent.nl/2017/05/25/inline-def-debugging/)
-   **Using def to subdivide programs:** [http://blog.cognitect.com/blog/2017/6/5/repl-debugging-no-stacktrace-required](http://blog.cognitect.com/blog/2017/6/5/repl-debugging-no-stacktrace-required)

# Unmap namespaces during experimentation

I use `ns-unmap` and `ns-unalias` to remove definitions from my namespace. These
are the complementary functions of `require` and `def`.

While exploring, you namespace will accrue failed experiments, especially around
naming. Instead of using a giant hammer [[tools.namespace](https://github.com/clojure/tools.namespace)], you can opt for
finer-grained tools like these.

Example:

    (require '[clojure.string :as thing])
    (ns-unalias *ns* 'thing) ; *ns* refers to the current namespace

# Re-open libraries for exploration

I use `in-ns` to jump into library namespaces and re-define their `vars`. I
insert bits of `println` statements to help understand how data flows through a
library.

These monkey-patches only exist in the running REPL. I usually put them inside a
 `comment` form. On a REPL restart, the library is back at its pristine state.

In this example below, I re-open `clj-http.headers` to add tracing before the
header transformation logic: [[source](https://github.com/dakrone/clj-http/blob/3.x/src/clj_http/headers.clj#L134-L140)]

    ;; set us up for re-opening libraries
    (require 'clj-http.headers)
    (in-ns 'clj-http.headers)
    
    (defn- header-map-request
      [req]
      (let [req-headers (:headers req)]
        (if req-headers
          (do
            (println "HEADERS: " req-headers) ;; <-- this is my added print
            (-> req (assoc :headers (into (header-map) req-headers)
                           :use-header-maps-in-response? true)))
          req)))
    
    ;; Go back to to the user namespace to test the change
    (in-ns 'user)
    (require '[clj-http.client :as http])
    (http/get "http://www.example.com")
    ;; This is printed in the REPL:
    ;;   HEADERS:  {accept-encoding gzip, deflate}

An astute observer will notice this workflow is no different from the regular
clojure workflow. Clojure gets out of your way and allows you to shape &
experiment in the code in the REPL. You can use this technique to explore
`clojure.core` too!

NOTE: I strongly advise this technique for local exploration only. Using this in
a production environment to debug is risky!

# Next Steps

I highly recommend readers to check out Stuart Halloway's talk at Strange Loop
2018: [Running with Scissors: Live Coding with Data](https://www.youtube.com/watch?v=Qx0-pViyIDU) for more ideas.
