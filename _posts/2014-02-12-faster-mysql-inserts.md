---
layout: post
title: "MySQL Speedup"
date: 2014-02-12 21:29
comments: true
categories: databases mysql
---

Last year, I was poking around with optizing MySQL usage, and I found myself thinking:

> Why is the system so slow at insertion and querying?
> Do I need to shard my database?


Despite SQL being a declarative solution where you should let the Query
Optimizer/Planner find the best execution path -- there are some tricks
developers should be aware of to improve read/write performance.

I have documented a few tricks for increasing read & write performance.

*Forutnately*, I did not resort to sharding because it's not fun from an
operational perspective.

## Increasing Read Performance

Firstly, I learned a lot from reading [Use the index, Luke][luke]. It is a
wonderful hands-on resource for choosing indexes to make SQL fast. After all,
the Query Optimzer cannot make your query fast unless you create good
indexes. Every application developer **should** [check it][Luke] out.

As for myself, I found these two tricks the most invaluable:

1. **Query using only the indexes if possible**: Indexes are fast, and you
   should consider indexing the field if use it alot in `WHERE` or `JOIN`. If
   you don't, use an index, you have to scan the table, which is slow.

2. **Use the *least* number of joins possible in nested subqueries**: In MySQL,
   the `JOIN` operation happens before `WHERE`. Prematurely `JOIN`ing tables
   together which get immediately filtered in the same `WHERE` statement slow
   applications down significantly.  In a previous job, I managed to speed up a
   query that took `30s` down to `300ms`. I moved the `JOIN` from a nested
   subquery into the outer query, I was able to filter down 1 million records to
   100 records before performing `JOIN`. Contrast that with `JOIN` 1 million
   records, then `WHERE` down to 100 records.

[luke]: http://use-the-index-luke.com

## Increasing Write Performance
At some point, you will want to insert tens of thousands of records at once into
your database. Maybe you're loading up test data, or you have a fat data
pipeline. I discovered two tricks to help increase insert performance by a
factor of 10.

I used [JUnit Benchmarks][junit-benchmark] to re-run the insert tests over **10
rounds**. In this test, I insert 56240 records into the same table with a
primary key & multiple indexes.

Note: These improvements are specific to **JDBC**. The techniques *may* apply to
other libraries/platforms.

### Initial Performance

```
Processing Data + Writing (56240 records)
 round: 16.49 [+- 0.69], round.block: 0.00 [+- 0.00], round.gc: 0.00 [+- 0.00],
 GC.calls: 97, GC.time: 2.28, time.total: 280.69, time.warmup: 115.76,
 time.bench: 164.93
```

Extrapolating the math, 56240 records / 16.49 seconds = **3400 inserts/second**

### Optimizations

Later, I re-ran the microbenchmark after trying these two things:

1. **Use `rewriteBatchedStatements=true`**: With this connection option, JDBC
   will try to pack as many statements into a single query. This saves roundtrip
   times with a lot of records.

2. **Reorder the Batch Statement Records**: I found that if you pre-sort your
   batch inserts in **primary-key** order, this considerably speeds up
   performance. Given that you probably have more worker nodes that database
   nodes -- you should **always** presort the data before sending it over the
   wire.  I suspect this is good because we don't have to thrash MySQL's index
   because insertion does not require frequent swapping of memory blocks. This
   may be an implementation detail specific to MySQL.

### Performance After Applying both Optimizations

I re-ran the same benchmark after applying both optimizations -- and the difference
was impressive.

```
Processing Data + Writing (56240 records)
 round: 1.68 [+- 0.16], round.block: 0.00 [+- 0.00], round.gc: 0.00 [+- 0.00],
 GC.calls: 73, GC.time: 2.09, time.total: 29.10, time.warmup: 12.27, time.bench:
 16.83
```

Extrapolating the math, 56240 records / 1.68 seconds = **33476
inserts/second**. This gives us roughly **10x** speedup.

In short: if your data is already pre-sorted by primary key order, MySQL will have much
less cache-misses for index creation, and thus speedup insertion performances.

[junit-benchmark]: http://labs.carrotsearch.com/junit-benchmarks.html
