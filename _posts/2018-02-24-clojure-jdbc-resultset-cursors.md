---
layout: post
title: "Using Cursors with Postgres and clojure.java.jdbc"
date: 2018-02-24
comments: true
categories: blog
tags: clojure sql jdbc
---

This post will show how to use cursors with [`clojure.java.jdbc`](https://github.com/clojure/java.jdbc) and
Postgres. [Cursors](https://en.wikipedia.org/wiki/Cursor_%28databases%29) allows SQL clients to traverse through large records.
JDBC Drivers use this feature to send batches of records to the client.

*This article assumes familiarity with `clojure.java.jdbc`.*

Setting up cursors with `clojure.java.jdbc` with Postgres is a little tricky.
The Postgres JDBC [docs](https://jdbc.postgresql.org/documentation/head/query.html#query-with-cursor) show you how to do it with PreparedStatements. I
prefer to use `clojure.java.jdbc`'s API where possible. 

On the surface, use of simple queries and cursors are indistinguisable. The only
way to know if we have performance benefits is to measure! **TL;DR** cursors
respond faster than simple queries for large queries.

This topic builds upon a blog post from 2010. [^1]

## Experiment Setup

The experiment will read ~1,500,000 record from a table. We examine three
metrics:

1. Time to first record from the database
2. Time to 10000th records
2. Duration to read all records

(1) and (2) is a measure of responsiveness. The faster your application receives
records, the sooner you can process records. (3) is a measure of throughput.

We measure two functions. `without-cursors` uses JDBC's simple
queries. `with-cursor` uses cursors for queries. This is database-engine
specific. See [Postgres JDBC Docs](https://jdbc.postgresql.org/documentation/head/query.html#query-with-cursor). Play close attention to use of
`transaction`, `:fetch-size` and `:result-type` to enable cursors.

```clojure
(defn without-cursor [db sqlvec f]
  (jdbc/query db sqlvec {:row-fn f}))

(defn with-cursor [db sqlvec f]
  (jdbc/with-db-transaction [conn db]
    (jdbc/query conn sqlvec {:row-fn f :fetch-size 10000 :result-type :forward-only})))
                                       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                                              These options are important
```

We measure with a helper function by recording elapsed time to stdout.
Measurements are executed in a Clojure REPL with several warmup runs. The final
run is recorded in the [Results](#results) table.

``` clojure
(defn new-measurement
  []
  (let [state (atom 0)
        start (System/currentTimeMillis)]
    (fn [x]
      (let [count (swap! state inc)]
        (when (or (= 1 count) (zero? (mod count 10000)))
          (println (- (System/currentTimeMillis) start) " at records: " count))))))

(without-cursor db ["SELECT * FROM events"] (new-measurement))
(with-cursor    db ["SELECT * FROM events"] (new-measurement) 10)
(with-cursor    db ["SELECT * FROM events"] (new-measurement) 100)
(with-cursor    db ["SELECT * FROM events"] (new-measurement) 1000)
(with-cursor    db ["SELECT * FROM events"] (new-measurement) 10000)
(with-cursor    db ["SELECT * FROM events"] (new-measurement) 100000)
```

This is an example of the output:

    178  at records:  1
    273  at records:  10000
    331  at records:  20000
    ...
    19767  at records:  1570000
    19778  at records:  1571000
    19788  at records:  1572000

## Results

|     Fetch Size | Time to first record (ms) | Time to 10000 record (ms) | Total duration (ms) |
|----------------|---------------------------|---------------------------|---------------------|
| without-cursor |                      3041 |                      3105 |               13656 |
|             10 |                         2 |                       359 |               58005 |
|            100 |                         2 |                       124 |               19029 |
|           1000 |                        13 |                        93 |               13735 |
|          10000 |                        24 |                        81 |               13617 |
|         100000 |                       186 |                       192 |               13066 |

Without cursors, we wait 3 *full* seconds before seeing any prints. With
cursors, the time is on the order of milliseconds. This indicates that cursors
are working! The total duration is consistent for `fetch_size > 1000`. From this
measurement, fetch size: 1000 has the best of responsiveness and throughput.

## Conclusion

Cursors are awesome! You should use if you can meet the constraints of the DB
engine. Your applications can be more responsive with cursors. Also, take the
measurements here with a grain of salt. You should measure whether your
application benefits from Cursors. 

[^1]: Kyle Burton wrote this up with an old version of clojure.java.jdbc whose API has changed. http://asymmetrical-view.com/2010/10/14/clojure-and-large-result-sets.html
