---
layout: post
title: "Coding Constraints as Design"
description: ""
category: programming
tags: [hci]
---

I am a hopeless romantic as a software craftsman. When I learn a better way to
code something that already exists, I tend to go against pragmatism. I would
happily spend time ripping through poorly though-out coding design: ripping
out interfaces, switching data structures, renaming variables. This continous
refinement of process has really helped me develop as a software writer. Over
this last year, I've accumulated some thoughts from working with various
programming languages.

## Use the simplest data structures possible.
When working with statically-typed OO languages, we often get stuck with
having to object hierarchies for everything. Type-safety is nice to have, but
at times the problem is simple enough that adding types to everything does not
accomplish very much. So what do we use instead of building specific types for
everything?

In contrast, there are three typical data structures developers should be
accustomed to:

    - Dictionary/Map
    - Sets
    - Lists

These fundamental data-structures are the building blocks of all systems.
They're also concisely represented. In one scenario, I had to model the steps
a user may behave when using our system. I initially thought I should build an
`Action` class with named parameters. Then perhaps I could create a method
which will perform an action. That's a very OO approach, but I thought I could
do something simpler. Why not use a list of lists? This is what I came up with
instead (with Python).

```python
time_cfgs = [
#   [`tag`,    `start_time`,    `end_time`, `frequency`]
    ["day-1", 1377673200000, 1377759600000, 3],
    ["day-2", 1377673200000, 1377759600000, 5],
    ["day-3", 1377673200000, 1377759600000, 3],
    ["day-4", 1377673200000, 1377759600000, 5],
    ["week",  1377673200000, 1378278000000, 6],
    ["month", 1377673200000, 1380351600000, 8],
    ["day-5", 1377673200000, 1377759600000, 5]
]
```

Here I chose a list to represent the work that needed to be done. I can see at
a glance all the configuration elements without the visual noise of
instantiating classes. Furthermore, I don't need to worry about the
interaction with classes. An list is all that there is.

Another advantage here is clear separation from what it does and how it does
it. Arrays are value objects. Very little assumptions need to be made, other
than a small definition as comments on the top.

## What Something Does Should Be Separated from How It Does it
Regardless of language idioms, application programming can always benefit from
good abstractions. An abstraction separates what it does from how it does it.
Keep the high level details in the abstraction, and leave the nitty gritty
implementation details hidden -- but accessible when needed. Three things I've
found that help with abstraction are:

    - interfaces/implementations
    - do-around blocks/decoration
    - combinators

Interfaces & Implementation is the separation from what it does and how it
does it. In Java, this is what interfaces/classes are. The beauty of this is
composing implementations together. For example: we have an interface
`CommentRepository` that fetches stored Facebook comments. First, we may store
this in a database, so we would implement this interface it
`DbCommentRepository`. Later on, we realize we need to improve performance and
so we want to include a caching layer. So, we could implement a
`cachedCommentRepository` that wraps an instance of `DbCommentRepository`. We
were can add new functionality without changing the internals of
`DbCommentRepository`. This is possible because we programmed to the
`CommentRepository` interface.

## Do-Around Blocks/Managed Contexts
Inevitably, when writing complex code, there will be components that need to
be done before and after a function is called. In functional languages, this
is easy because functions are treated as variables and passed around. In
languages like Java, this would typically involve *alot* of copy and pasting.
This becomes a maintenance nightmare if the implementation were to change
because you need to trust that you remember to copy/paste to all the
locations. In Java, you can simulate functions with the Command pattern.

Here's an example. My main goal is to delete a a comment from a post.
Additionally, I need to notify a scheduled task to update both the post by
setting their time stamp.

```
public void deleteGroup(long groupId) {
    Group group = groupRepo.fetch(groupId);
    List<Users> users  = groupRepo.findUsers(group);
    groupRepo.destroy(group                           // delete comment
    for (user : Users) {
        userService.update_timestamp(user)            // for re-indexing
    }
}
```

Okay... so imagine this can happen all the time where you're deleting stuff
and realize you need to propagate changes. What if in the future we decide we
need to make this happen real-time b/c updating the time stamp is too slow. We
want to queue up the work to be done later. If we need this often --
refactoring is going to suck. We need to be surgically precise when making
these changes. So how does the command pattern help? Let's introduce what our
abstraction would be:

```
public interface IAction { public void do() }

public class UserService {
    public void withChangeTo(List<User> users, IAction) {
        IAction.do();
        for (user : users) { this.update_timestamp(user) }
    }
}
```

Now we've hidden away how the changing mechanism is. All that you need to know
is *if you are changing a user, wrap it with `withChangeTo`*. Now the
implementing deleteGroup method can look like this [using Java 8 Lambdas
(shortcut for single-method classes)]

```
public void deleteGroup(long groupId) {
    Group group = groupRepo.fetch(groupId);
    userService.withChangeTo(groupRepo.findUsers(group)), () -> {
        groupRepo.destroy(group);
    });
}
```

The key fact here is originally, we exposed an implementation detail: updating
the index is done by setting the time stamp. What we really *should* be saying
is: **re-index these users who are affected**. So to wrap it up, the real-time
queue would only require changes to UserService which will propagate to
**all** calls to `withChangeTo`. Neat!

```java
public class UserService {
    public void withChangeTo(List<User> users, IAction) {
        IAction.do();
        for (user : users) { indexTaskQueue.add(user) }
    }
}
```

Now, let's say we want to add an additional interface -- one that is more
lazy. Instead of specifying users, we want to only specify a query that
describes which users are affected. An example is a query for Group. Our
hypothetical query would look like this: The nice thing is now we don't even
need a userService to potentially get users from. We have a representation of
where these users come from.

```
new UserQuery().where("group", groupId);
```

We can't run this query after deleting the group because the users will no
longer exist. Let's write a withChangeTo that takes in such a Query.

```java
public UserService() {
    public void withChangeTo(UserQuery query, IAction) {
        List userIds = this.queryForIds(query);
        IAction.do();
        indexTaskQueue.addAll(userIds);
    }
}
```

Two things happened. 
1. We decoupled getting concrete users from where these users come from. This
   UserQuery object can be used to lazily or eagerly fetch users. It may only
   return ids -- it doesn't really matter. Before, we were tied to getting
   concrete user classes which may be expensive but unnecessary. Now we don't
   have to deal with it.
2. We have a 'sandwich' on IAction.do(). We can't possibly do this without
   getting the userIds beforehand because if we do the query after -- we no
   longer know which users were part of the group. Once again, if we had to
   surgically add this to wherever reindexing is needed, it would be a
   *nightmare*.

## Wrap-up
With the features here, the key takeaway is that we want to write code that is
easy to comprehend. I can probalby go on about other facts, but I'll save it
for a part 2.
