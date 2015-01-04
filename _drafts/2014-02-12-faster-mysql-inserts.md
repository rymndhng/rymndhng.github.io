---
layout: post
title: "MySQL Speedup"
date: 2014-02-12 21:29
comments: true
categories: SQL, Database
---

Last year, I was poking around with optizing MySQL usage. Why is it slow when
I'm querying & inserting data? Well, despite SQL being a generalized solution
where you *should* Query Optimizer will try to find the best execution path --
some things simply cannot be optimized. I found one way to increase read
performance, and another to increase write performance.

## Increasing Read Performance
This provides some guidelines if you have nested subqueries and joining multiple tables.

1. **Use the *least* number of joins possible in nested subqueries**: In MySQL,
the `JOIN` step happens before `WHERE` get applied. If you prematurely `JOIN`
table, you perform unnecessary `JOIN` operations because many of those rows will
probably be filtered out in the `WHERE` statement. Where I worked previously, I
managed to shrink a query that used to take `30s` down to `300ms` because the
table had nearly a million records, and the query was prematurely joining all the
records before filtering.

2. **Query using only the indexes if possible**: Indexes are fast, if you don't
   use an index, you have to scan the table, which is slow.

[Use the index, Luke][luke] is really good resource to learn how to make SQL
queries fast. Every application developer **should** check it out.

[luke]: http://use-the-index-luke.com


## Increasing Write Performance
At some point, you will want to insert tens of thousands of records at once into
your database. Maybe you're loading up test data, or you have a fat data
pipeline.

I discovered two tricks to help increase insert performance by a factor
of 10. Note: I am using an SSD on my own machine.

I used [JUnit Benchmarks][junit-benchmark] to re-run the insert tests over 10
rounds. In the test, I try to insert 56240 records.

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

1. **Enable `rewriteBatchedStatements=true`**: With this connection option, JDBC
   will try to pack as many statements into a single query. This saves roundtrip
   times with a lot of records.

2. **Reorder the Batch Statement Records**: I found that if you pre-sort your
   batch inserts in **primary-key** order, this considerably speeds up
   performance. Given that you probably have more worker nodes that database
   nodes -- you should **always** presort the data before sending it over the
   wire. I suspect this is good because we don't have to thrash MySQL's index as
   frequently (implementation detail perhaps?)

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

### Analysis
You should really try applying both optimizations -- especially when inserting
large datasets.

You may think that it doesn't really matter since we'll leave the database to
sort the incoming data -- but (I think) MySQL doesn't care how the data comes
in. When it's parsing the rowset, it's going to need to build indexes in the
order data comes in. What this means is, the system will crawl when building
indexes because it constantly needs to invalidate the cache by switching between
different b-trees.

If your data is already pre-sorted by primary key order, MySQL will have much
less cache-misses for index creation, and thus speedup insertion performances.

[junit-benchmark]: http://labs.carrotsearch.com/junit-benchmarks.html
