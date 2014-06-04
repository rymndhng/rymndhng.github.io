---
layout: post
title: "Ruby Inheritance Chain"
description: ""
category: 
tags: []
---

I am slowly grasping why Matz wants Ruby to be a fun language to prgram in. These dynamic
languages help us seriously avoid boilerplate code.

# Multiple Inheritance
The OO model has difficulty addressing this. We want the flexibility of a type system without
ambiguity, and one of these debated areas is multiple inheritance. C++ allows for multiple
inheritance, while Java does not. 


Let's say a `Beaver` is both a `LandWalker` and `Swimmer`. In this case, our animal want to
inherit the functionalities of both Playtypus, in short Beaver needs both LandWalking and
Swimming methods. Both `LandWalker` and `Swimmer` have a method `move`.

(todo: is this right)
In C++, confusion arises when we go up the inheritance chain b/c we don't know which method
will be called. The C++ way depends on casted type at runtime to determine the behavior. This
means... boilerplate!

In Java, we can't do multiple inheritance, so we would have to create Interfaces called
LandWalker and Swimmer. Now, this sucks b/c interfaces only specify the method names but not
their implementation. The `moves` method is fine, but what about methods specific to
LandWalker or Swimmer? Boilerplate!

In Ruby's case, we would have a mixin called LandWalker and Swimmer. We have
