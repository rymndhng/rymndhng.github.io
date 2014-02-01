---
layout: post
title: "Permutations in Java/Scala"
date: 2013-06-23 03:14
comments: true
categories: [algorithms, Java, Scala]
---

In the process of studying for programming interviews, I stumbled across a problem which involved finding permutations of a list of string elements. The implementation the book describes uses a Permutation class as follows:

```java
public class Permutations {
  private boolean[] used;
  private StringBuilder out = new StringBuilder(); 
  private final String in;

  public Permutations( final String str ){ 
    in = str;
    used = new boolean[ in.length() ]; 
  }

  public void permute( ){
    if( out.length() == in.length() ){
      System.out.println( out );
      return; }
      for( int i = 0; i < in.length(); ++i ){ if( used[i] ) continue;
        out.append( in.charAt(i) );
        used[i = true];
        permute();
        used[i] = false;
        out.setLength( out.length() - 1 );
      }
    }
  }
}
```

Having not dealt with recursion in Java, I was initially surprised that `out` (a mutable variable) was passed along during the recursive permute calls. Later, I found out the JVM copies all function parameters and instance variables into a new stack frame. Yes, this is how the language works - and I should probably know this, but after coding in Scala, this is a good reason to push for using immutable classes. With immutable classes, I don't have to worry about the ambiguity of whether side-effects happen to mutable variables. 

Furthermore - I don't think this is good design b/c we're basically this class's goal is to do Permutations. Classes should model the business domain. Permutations is probably not going to be a domain model. I can understand why it's laid out like this in a Programming Interview book, but just some food for though.

Being unsatisfied with this algorithm, I wondered what an idiomatic Scala implementation of permute would be. I tried the following:

I found three things:
1. Scala's (`SeqLike`)[https://github.com/scala/scala/blob/v2.10.2/src/library/scala/collection/SeqLike.scala#L1] trait which implements permutations
2. A StackOverflow member's implementation [here](http://stackoverflow.com/questions/5056669/scala-permutation-of-factorials?answertab=active#tab-top")
3. My modified version of **2** to improve performance as below:

```scala
def permute[T](remaining: List[T]):List[List[T]] = remaining match {
case Nil => List(Nil)
case _ => for {
    (x,i) <- remaining.zipWithIndex;
    (l,r) = remaining.splitAt(i-1);
    res   <- permute(l:::r.tail)
  } yield (x::res)
}
```
### Analysis
Without heavily scientific evidence, I used testing. Benchmark to compute the time for permuting (1 to 6).toList. First came **(1)**, followed by **(2)** and **(3)**.

**(3)** and **(2)** have both (surprisingly) split operations of *O(n)* because **(3)** performs a diff of a single element. **(2)** is cleaner in terms of syntax (I did not think of using set operations).

My original intention was to try (1 to 20), but soon realized that combinatorial expansion would cause overflow errors (hint: compute 20!). Even at 6, the performance of **(2)** and **(3)** are horrendous. **1** performed ~10x faster at least. When I dived into the source code, the Scala contributors actually use an iterative approach to get the most performance (not surprised). This design sense since the implementation is not as elegant, why not provide it as a convenience.

### Conclusion
The `SeqLike` trait's permute is basically awesome, and you should have no qualms about using it over rolling your own. The nice thing of such a design is to provide a clean interface while providing a messier solution to gain performance.

###### Caveats/Followups
- Should try comparing this to Java version in book
- Need more specific testing
- Can I parallelize this?
- Should understand what's happening in the `SeqLike` version
- How is recursion implemented in other languages? -- Do all JVM languages copy all instance/parameters into new stack frame? How about in other languages such as Ruby? Is there a design where say... none are, or only one but not the other?
