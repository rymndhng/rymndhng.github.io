- Anecdotal experience, was asked to decipher code for an interview -- but I
  could not get past all the variables. Too many iterators, i++, while loops.
  Yes, they're fundamental constructs but don't succintly express the business
  logic, instead head gets stuck trying to track all these variables in working
  memory -- which I could not do. ( 5 pages of java code)
- so I thought: what's a better way to express this solution. let's walk through
  that here.


Let's say we have a few data sets which describe where the user lived over time,
what car he owned, and what company he worked at. What we want to compute is the
intersection between where the person lived, what car he/she owned, and what
company he/she worked at.

I.e. let's say we have these three datasets as a small list of tuples:

```scala
val cars = [("2004-10","Corolla"), ("2009-5", "Civic")]
val city = [("2000-01", "Vancouver"), ("2009-01", "Toronto")]
val work = [("2000-03", "Objects. Inc"), ("2008-10", "Functional Corp")]
```

What I want to produce is a single list like so ordered by timestamp.
```scala
val output = [("2000-01", Nil, "Vancouver", Nil),
              ("2000-03", Nil, "Vancouver", "Objects. Inc"),
              ("2004-10", "Corolla", "Vancouver", "Objects. Inc"),
              ("2008-10", "Corolla", "Vancouver", "Functional Corp"),
              ("2009-01", "Corolla", "Toronto", "Functional Corp"),
              ("2009-05", "Civic", "Toronto", "Functional Corp")]
```

Let's start this with a bit of psuedo programming:

```
def intersect(datasets)
  return if all elements of data sets has size 1

  current_items = take first element else nil of each from datasets
  start_ts = min of current_items
  produce start_ts, current_items[0] ... current_items

  next_datasets = advancing the datasets which have items where
    their first element.ts == start_ts

  recursively call intersect(next_datasets)
```

State is the devil:
- cannot cache
- cannot compare reference value
- valid now != valid later, "defensive programming"
- trivially multithreaded
- not referentially transparent
  - numbers are: 1 is always 1, int a = 1; int a = 5



- Programming starts with counting, familiar with for-loop. Depending on the
  tools given to you, that might be all that you end up working with, the for
  loop workhorse.
- In a different camp: functional programming, the for loop discarded for higher
  order functions and recursion. Today I will discuss why this is better, but
  let's start with a problem statement to solve:


Abstractions:
- seq interface: item.head, item.tail is good
  - infinite lists (for generation)
  - stream processing (websockets? twitter api)
- deal with value objects, not counters -> NO out of Bounds Exception
