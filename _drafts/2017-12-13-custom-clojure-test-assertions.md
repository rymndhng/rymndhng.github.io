---
layout: post
title: "Custom clojure.test Assertions"
date: 2017-12-13
comments: true
categories: clojure patterns testing
---

Custom test assertions increase developer productivity. It reduces boilerplate
in test code. When implemented well, it helps developers identify where the test
failures are.

`clojure.test` has a mechanism for extending the test assertion that any
intermediate developer can sink their teeth into.

Implementing custom assertions uses Clojure Macros so it's recommended to brush
up on understanding the basics. I like [Clojure for the Brave &
True](https://www.braveclojure.com/writing-macros/).

For the purpose of demo, we're going to implement a custom assertion `truthy?`.
The goal is to be able to write this test:

``` clojure
(deftest my-test
  (is (truthy? 1)))
```

The final solution looks like this:

```clojure
(defmethod clojure.test/assert-expr 'truthy? [msg form]
  (let [expr (second form)]
    `(if ~expr
       (t/do-report {:type :pass :message msg
                     :expected '~form :actual "..."})
       (t/do-report {:type :fail :message msg
                     :expected '~form :actual "..."}))))
```

The following sections will explain the three parts of a custom `clojure.test` assertion:

1. Registering the custom assertion
2. Implementing the assertion
3. Explaining the test result using clojure.test/do-report

### 1. Registering the custom assertion

``` clojure
(defmethod clojure.test/assert-expr 'truthy? [msg form]
  ;; ...
  )
```

This is done by extending the multi-method `clojure.test/assert-expr`. 

The arguments are msg and form. The `msg` is a custom error message (may be
empty). The `form` is a s-expression of the test.

### 2. Implementing the assertion

``` clojure
(defmethod clojure.test/assert-expr 'truthy? [msg form]
  (let [expr (second form)]
    `(if (true? ~expr)
        ;; do something if true
        ;; do something if false
       )))
```

Your custom assertions should return new code for `clojure.test` to evaluate,
similar to `defmacro`. For implementing truthy, we need to do two things:

1. extract the test expression
2. return a new s-expression for clojure.test to evaluate

In the example: `(is (truthy? 5) "hello")`, the arguments given to our assertion are:

- `msg: "Hello"`
- `form: '(truthy? 5)`

The `form` contains the entire test expression. We need to call `(second form)`
to extract the `5` from `form` to build our test assertion.

### 3. Explaining the test result using clojure.test/do-report

``` clojure
(defmethod clojure.test/assert-expr 'truthy? [msg form]
  (let [expr (second form)]
    `(if ~expr
       (t/do-report {:type :pass :message msg
                     :expected '~form :actual "..."})
       (t/do-report {:type :fail :message msg
                     :expected '~form :actual "..."}))))
```

Use `clojure.test/do-report` to mark an assertion as passing, failing or
erroring. This function takes a map of options.

The `:type` is one of `:pass`, `:fail`. You may use `:error` when the unexpected
happens, i.e exception thrown.

The keys `:message`, `:expected`, `:actual` are used to provide additional
context as needed to understand the test failure.

## When to use this pattern

You should consider this pattern if you are frequently:

- creating per-assertion setup/teardown
- need to format the test failure messages

I've found this pattern to be invaluable for instrumenting our HTTP Client to
check for RAML API Compliance. Our test assertion looks like the following:

``` clojure
(is (valid-raml? (http/get "http://my.api/leads")))
```

This custom assertion does the following:

- Adds additional clj-http middleware to capture the request/response of the HTTP call
- Executes the test body, presumably using clj-http
- Checks the captured request/response for RAML compliance and report any failures

## Gotchas
Because this extends the built-in clojure.test library, new developers may be
confused at what this piece of code does. It is opaque and you are unable to "Go
to Definition" when hovering over a custom assertion.

