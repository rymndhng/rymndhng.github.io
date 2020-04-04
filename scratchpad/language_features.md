# Programming Languages & Creativity
When I started learning to learn software development, I was handed a
language which was taught by my class, and at the time it was Python.
As a starter language it was great, it was easy to comprehend and
worked at a high level. This is a variable, that is a loop, and that
other thing is a function.

Later on, I moved on to dabble with C and Java. And oddly for the most
part of my undergraduate, courses would have work in C or Java. As a
new developer, all programming languages looked the same to be. There
are objects there are methods, it does a little bit of computation,
and I have an answer to my problem.

I suspect other starting developers feel the same. If I need to solve
a problem (in school), I'm going to pull up the langauge I'm most
comfortable with and hack away at it until it works. And for quickly
hacking things it works great, until you want to reuse code.

I find it interesting how you would approach this in different
programming languages. Some features are simple to abstract away, it
boils down to this process:

1. Figure out which section of code is duplicated
2. Write a function with parameters that expose the variable pieces
3. Write some tests to make sure it works
4. Refactor code to use function

This is a good approach, but it works well for simple features, but in
software engineering, you may need to build more complex components
where it's not so clear cut, for example, an object relational mapper
(ORM). In these scenarios, a naive software developer may take one of
two approaches:

1. Put more functionality in a function, and consequently add more
   parameters to the function definition.
2. Add more functions at the cost of duplicate code.
                                                                           
Both of these options are non-optional.

### Add more functionality into the same piece of code
The positive of this approach is that code is DRY. You minimize

When you do this, you end up colluding the intentions of the function.
If only 2 parameters are mandatory and the other 7 require a
reasonable default, the working memory of the developer slows down
development, especially if they're learning how to use a use API.

(insert picture of Spring JDBC Template, SQL wrappers)
Furthermore, the code is less maintable when you need to ensure all
the different input parameters play nicely.

### Add more functionality at the cost of duplicate code
The positives of this approach is increasing the surface area of your
API to reduce the depth to understand. And this is good because you
don't end up making different different components talk to each other.

However, you lose extensibility. Because you already started
duplicating code, when you want to add a new feature to your
component, you will need to duplicate code again and so forth.

## There is no clear solution

In software engineering, there are two types of complexities:
_incidental_ complexity and _accidental_ complexity. **Incidental
Complexity** is complexity related to the problem you're trying to
solve, i.e. calculating how much I will receive in my tax return.
**Accidental Complexity** is complexity that comes out of coding up
the solution.

Incidental complexity cannot be avoided. It's inherent to the problem.
If you're writing a program to calculate how much tax return a person
gets this year, you **must** follow the specification handed out by
the government. If you calucation chooses a lower tax rate than the
government, it wouldn't be a very good tax calculator would it?

Accidental complexity also cannot be avoided, but should ideally be
minimized. Once again, you're trying to calculate how much tax return
a person gets back this year, so how can you write a succint solution.
Perhaps your intention involves coding it quickly so you can get it to
market, increasing the reach of the program, by wrapping it in a
webapp, scaling it out to thousands of users, making it extensible so
you don't need to start from scratch because next year's tax rules are
slightly different.

Both the examples in the previous section are examples of accidental
complexity. The overall goal was to reduce complexity by:

1. Put more functionality in a function to increase code-reuse. You
   can think of this add adding more **depth** to your method.
2. Add more methods functionality to an API to reduce the complexity
   of each individual method, adding more **surface area**.

In either of these cases, it appears to be a minimization problem. You
want to minimize depth, but also minimize surface area, but the volume
of the code is the same. In other words, complexity isn't reduced, but
shuffled around. So what are some ways of solving these problems?

## If all you have is a hammer, everything looks like a nail.
A hammer is not a very elegant tool, but it gets the job done. Knowing
only a single programming language (or paradigmn) is like only owning
a hammer. You only know of how to solve problems in the context of the
tools available. Let's say I need to put some screws in a piece of
metal, and all I have is a hammer. What are my options to complete the
task? (hint: involves banging the screw into the metal). Does this
solution work? Yes, I suppose so, it's holding my metal together, but
is it the best solution (is it easy, fast, strong)? It's questionable.
This is a case of accidental complexity: you chose the wrong tool for
the problem, and ended up with a suboptimal solution.

To draw parallels to building web applications, there is a giant
mismatch between the environment and working with a statically typed
language like Java. In webapps, you're working between three layers:
HTTP, Webapp (in the programming langauge of your choice), and
Persistence (SQL etc).

HTTP is not a type-safe environment. Persistence is also not a
type-safe environment. So when you choose Java as the intermediate
layer, you're shoehorning a thin layer of type-safe java code between
two layers that are definintely *NOT* type safe. You convert HTTP form
submissions into Form Objects with getter/setter methods to
encapsulate the form, and then when you want to save it, you serialize
these Form Objects or maybe Domain Objects into rows in a SQL
database. Notice these features are used to write layers to translate
between the environments on on top and below your web application.
This is accidental complexity. Back to the tax return calculator,
these components don't take your software closer to satisfying the
specification of calculating a tax return, merely cruft to translate
between the (accidental) layers introduced in your application stack.

So this brings us to the discussion of web frameworks. You get
monstrous web frameworks like Spring which try to solve these
problems, but you end up with cargo-cult(?should i use this) code,
Form objects, get/set methods, heavy use of annotations (i.e. JSON
parsing, object validation) where you need a deep understanding of the
framework to understanding when they're used. 

Contrast this to dynamic languages, like Ruby or Python. There's more
emphasis on simple data structures, lists, dictionaries, strings,
numbers for form parsing and translation from SQL.


As a software developer, our creativity to solve problems are highly influenced
One of my personal goals is to understand different programming
paradigms. 


# Notes
- creativity
- what is incidental and accidental complexity?
- constrained by our tools
- starting devs pick one lnaguage and stick to it, why is feature x
  not in language y
  - we are constrained by our 
- different layers serve different purposes
  - systems programming/middleware
  - app <-- what people are most do!

# Systems programming (C, Java, C++)
- typically high performance components
  - creativity means how to squeeze more performance
