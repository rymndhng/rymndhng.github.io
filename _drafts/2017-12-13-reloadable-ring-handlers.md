---
layout: post
title: "Custom Clojure Test Assertions"
date: 2017-12-13
comments: true
categories: clojure patterns
---

Clojure comes with a built-in testing library `clojure.test`. The library tiny.
For consumers of the library, there's four constructs: `deftest`, `testing`,
`is`, `are`.

This solves most test cases. You can make any truthy assertion with `is`.

``` clojure
(deftest testing-equality
  (is (= 1 0)))
```

Another nice feature of `clojure.test` is the ability to report on what caused
the test assertion to fail. It leverages macros to understand the syntax of the
code under test whereas other languages see a value (either true or false). This
has the benefit of creating useful error messages for any test assertion.

``` clojure
(is (< 1 0))
```

In other languages & test libraries, you need to lean on using custom assertions
to get useful error output. For example, this JUnit test relies on a
`greaterThan`.

```
assertThat("timestamp",
           Long.parseLong(previousTokenValues[1]),
           greaterThan(Long.parseLong(currentTokenValues[1])));
```

In short, `clojure.test` allows you to express the essence of your test using
assertions built-in to the language rather than having to use test-library
assertions.

## Custom Assertions
Assertions can easily tell you when a statement is true or false. It can help
you correct or identify mistakes too if you can give it enough hints.

In the case of the JUnit example, the programmer needed to provide hints
`greaterThan` to make the test reporting more useful in the case of failure. The
`clojure.test` implementation doesn't need this extra hint for this particular
case.

You can extend the built-in assertions by extending the clojure.test multimethod:

```clojure
(require '[clojure.test :as t])
(defmulti t/assert-expr custom-assert!
  ;; implementation
  )
  
;; To use this
(is (custom-assert! foo))
```

### When to use this pattern

You should consider this pattern when:
- you need per-test granualarity for specific setup/teardown actions
- you frequently need to format the test failure messages to be meaningful

A case where we've found this invaluable is for performing RAML API and
JsonSchema validations. Our integration tests use the clj-http client to make
requests to our API. For each HTTP request we want to assert compliance with our
RAML spec.

For example, our custom assertion looks like:

``` clojure
(defmulti t/assert-expr valid-raml? 
  ;;
  )

(is (valid-raml? (http/get "http://foo.bar.baz")))
```

The `valid-raml?` assertion could do the following:

1. Setup: Instruments clj-http with a custom middleware to check for API spec compliance
2. Executes the body of the assertion using `http/get`
3. Teardown: Report any API spec compliance issues

This approach has reduced the boilerplate of our tests significantly. It easily:
- tells us which line in the test caused the failure
- formats the test failure so the developer can easily pinpoint what went wrong

## Gotchas
Because this extends the clojure.test library, new developers may be confused at
what this piece of code does. When they try to get docs on this function, it
will do nothing. 

