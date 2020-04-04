---
title: Minimal Differential Sync with Clojure
layout: post
category: clojure
tags: toolbox
---

Collaborative text editing tools like Google Docs depend on having an algorithm to synchornize changes between multiple clients. One algorithm to implement synchronization is Differential Synchronization by [Neil Fraser](https://neil.fraser.name/writing/sync/).

DiffSync works conceptually similar to Git. A single document needs to synchronize to each other (pushed/rebased). To do this, you keep a copy of your previous state (the shadow) and the client constantly rebases off the shared state and then applying local changes on top. This process continues until both clients reach a steady state.

NOTE: This means there *does* need to be a mediator who can resolve merge conflicts.

Here's a simple implementation I threw up in Clojure making use of atoms and core.async. I found Clojure to map well to this problem space:

- history is maintained by persistent datatstructures
- atoms map well to a document which needs to  synchronize
- atom hooks make it easy to observe changes and push changes
- core.async: provides an abstraction when working with concurrent operations. i.e. we're listening to changes from multiple sources but we only ever want to operate serially.


NOTE: this is built on top of [clj-diff](https://github.com/brentonashworth/clj-diff). [^1]

```clojure
(ns entangle.core
  (:require [clojure.core.async :as a]
            [clojure.test :as test]
            [clj-diff.core :as diff]))

(defn empty-patch?
  "Needs work."
  [patch]
  (or (empty? patch)
  (= patch {:+ [], :- []})))

(defn rebase
  "Rebases the current state against head after applying patch."
  [head base patch]
  (let [working-changes (diff/diff base head)]
    (-> base
      (diff/patch patch)
      (diff/patch working-changes))))

(defn start-sync
  "Start synchronization of atoms whose state changes are propogated when it's
  state changes or when a patch is sent via data-in.

  Returns a channel which produces 'true' when both sender & receiver are in
  full sync.

  ref      - the reference object to synchronize
  data-in  - core.async channel for writing patches to
  data-out - core.async channel for reading patches from
  id       - id for debugging
  "
  ([ref data-in data-out] (start-sync ref data-in data-out nil))
  ([ref data-in data-out id]
   (let [cur-value @ref
         user-changes (a/chan)
         synced-ch (a/chan (a/sliding-buffer 1))]
     (add-watch ref :diff-sync #(future (a/>!! user-changes %&)))
     (a/go-loop [shadow cur-value]
       (a/alt!
         data-in ([patch ch]
                  ;; (println (str  id " patch " patch ":" shadow))
                  (if (empty-patch? patch)
                    (a/>! synced-ch true)
                    (a/thread (swap! ref rebase shadow patch)))
                  (recur (diff/patch shadow patch)))
         user-changes ([[key ref old-state new-state] ch]
                       ;; (println (str id " watch " key ":" ref ":" old-state ":" new-state ":" shadow))
                       (let [patch (diff/diff shadow new-state)]
                         (when (empty-patch? patch)
                           (a/>! synced-ch true))
                         (a/>! data-out patch)
                         (recur new-state)))))
     synced-ch)))
```

Example of usage:

```clojure
(def a (atom ""))
(def b (atom ""))

(def data-> (a/chan 1))
(def data<- (a/chan 1))

(start-sync a data-> data<-)
(start-sync b data<- data->)

(swap! a %(str "fooo"))
@b
#=> "fooo"
```

A great extension would be to expose this over websockets and use core.async on the otherside. This code is clojurescript compatible. I have a small repo where I was experimenting with this.[^2]


[^1]: Pending a [pull request](https://github.com/brentonashworth/clj-diff/pull/6)

[^2]: [Test code](https://github.com/rymndhng/entangle) of differential sync
