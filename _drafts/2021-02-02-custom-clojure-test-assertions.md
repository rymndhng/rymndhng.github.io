---
layout: post
title: "Custom clojure.test Assertions"
date: 2021-02-02
categories: clojure current-practise
---

Good test assertions help developers to write maintainable tests. Using the
correct assertion helps developers debug failing tests.

I like sticking to `clojure.test` because you can get a lot of mileage out of
using the `is` macro. It works really well most of the time.


``` clojure
(is some-expression)

(is (= a b))
(is (= 3 5))
(is (> 1 2))
```

What I like is that the `is` blocks do these three things well:

1. The API is small.
2. The error reporting works well.
3. It can bolt on top of any truthy assertion.

The default error reporting tries to output the asserted values to show you
where the error is. For complex assertions, sometimes the best it can tell you
is ``(!= true false)`. This is not helpful, we can do better.

### Custom Assertions

`clojure.test` can be extended to add additional assertions using `defmethod`.

*NOTE*: Implementing custom assertions uses Clojure Macros so it's recommended
to brush up on understanding the basics. I like [Clojure for the Brave &
True](https://www.braveclojure.com/writing-macros/).

Here is a very simple custom assertion and the implementation. 

``` clojure
;; This is the test
(deftest my-test
  (is (truthy? 1)))
  
;; This is the custom assertiond
(defmethod clojure.test/assert-expr 'truthy? [msg form]
  (let [expr (second form)]
    `(if ~expr
       (t/do-report {:type :pass :message msg
                     :expected '~form :actual "..."})
       (t/do-report {:type :fail :message msg
                     :expected '~form :actual "..."}))))
```

The rest of this article with walkthrough how to implement a custom
`clojure.test` assertion.

## 1. Registering the custom assertion

This is the easy part. Extend the multi-method: `clojure.test/assert-expr`. 

``` clojure
;; How to register a multimethod
(defmethod clojure.test/assert-expr 'truthy? [msg form]
  ;; body
  )
```

`clojure.test` matches assertions based on the first symbol in the _form_.
`form` is the un-evaluated test body. `msg` is an optional error message
provided by the user

``` clojure
;; Example Forms

(is (= foo bar) "this is a test")
;; form => (= foo bar)
;; msg => "this is a test"


(is (truthy? my-data))
;; form => (truthy? my-data)
;; msg => nil

(is true)
;; form => true
;; clojure.test falls back to evaluating the expression as truthiness if there are no matching assertions
```

## 2. Implement the assertion

The body of custom assertion is a macro. The custom assertions should return a
new _form_ for `clojure.test` to evaluate, similar to `defmacro`. 

The _form_ can be expanded to any valid Clojure code. This is a very powerful
way to organize test code. It allows you to "hide" the implementation details of
the implementation without adding unnecessary indirection.  

Here's the implementation of `truthy?`. 

``` clojure
(defmethod clojure.test/assert-expr 'truthy? [msg form]
  (let [expr (second form)]
    `(if (true? ~expr)
        "yes it is"
        "no it is not"
       )))
```

If you're unfamiliar with macros, this code is extracting the argument to
`truthy?`, and rewriting the code to be `(if test then else)`. You can check
this in the Clojure REPL by macro-expanding the test assertion.

``` clojure
;; Custom Assertion
(is (truthy? 5) "hello")


;; Check the macroexpansion
(macroexpand '(clojure.test/is (truthy? 5) "hello"))

;; result
(try
  (if (clojure.core/true? 5)
    "yes it is"
    "no it is not")
  (catch java.lang.Throwable t__9706__auto__
    (clojure.test/do-report {:type :error, :expected (quote (truthy? 5)), :actual t__9706__auto__, :message "hello"})))

```

## 3. Explain the test result using clojure.test/do-report

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

Should you start writing custom assertions right away? It depends.

Most of the time, you should use the built-in assertions because they work very well.

I would advocate for writing custom assertions if you are running into issues
understanding your test code:

- There's a lot of code involved to massage the expected & actual arguments
  before calling an assertion.
  
- The test failure messages are not meaningful.

I've found this pattern to be invaluable for instrumenting our HTTP Client to
check for API Specification Compliance. Our test assertion looks like the
following:

``` clojure
(is (valid-specification? (http/get "http://my.api/leads")))
```

This custom assertion does the following:

- Adds additional clj-http middleware to capture the request/response of the HTTP call
- Executes the test body, presumably using clj-http
- Checks the captured request/response for API Specification compliance and report any failures

## Gotchas
Because this extends the built-in clojure.test library, new developers may be
confused at what this piece of code does. It is opaque and you are unable to "Go
to Definition" when hovering over a custom assertion.

