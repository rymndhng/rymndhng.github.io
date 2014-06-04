---
layout: post
title: "MySQL Speedup"
date: 2013-09-03 21:29
comments: true
categories: SQL, Database
---

Normally, with Database backed applications, the most important thing is
developer productivity, or rather get things done. It's easy to overlook the
finer details of the systems you're using. In this case, we'll use MySQL
inserts.

Most systems have a 'query' speed performance, and there are definitely ways to
overcome this. But very seldom do we discuss write performance. If you're
building backend services, performance is critical. Here are some nuggets I
discovered in the process of trying to optimize MySQL batch inserts using JDBC.

Note: my system uses an SSD.

Before Performance:
```
Processing Data + Writing (56240 records)
 round: 16.49 [+- 0.69], round.block: 0.00 [+- 0.00], round.gc: 0.00 [+- 0.00],
 GC.calls: 97, GC.time: 2.28, time.total: 280.69, time.warmup: 115.76,
 time.bench: 164.93
```

This gives us 3400 inserts/second.


1) Enable `rewriteBatchedStatements=true`
This is where *alot* of performance improvement comes from. TODO: I don't know
internally waht's going on to make this speedup but it matters. (Maybe it has
something to do with number 2).

2) Order the data in BatchInserts
Sorting order matters in BatchInsertions. If your data is already pre-sorted,
you can get up to 10-20x speedup with JDBC. Your application probably sorts data
sturctures much faster in memory.

After performance
```
56240 records
 round: 1.68 [+- 0.16], round.block: 0.00 [+- 0.00], round.gc: 0.00 [+- 0.00],
 GC.calls: 73, GC.time: 2.09, time.total: 29.10, time.warmup: 12.27, time.bench:
 16.83

```

This gives us a 10x speedup already.

33,476 insertions/second.


You may think that it doesn't really matter since we'll leave the database to
sort the incoming data -- but (I think) MySQL doesn't care how the data comes
in. When it's parsing the rowset, it's going to need to build indexes in the
order data comes in. What this means is, the system will crawl when building
indexes because it constantly needs to invalidate the cache by switching between
different b-trees.

If your data is already pre-sorted by primary key order, MySQL will have much
less cache-misses for index creation, and thus speedup insertion performances.



There are several optimizations in place that that
