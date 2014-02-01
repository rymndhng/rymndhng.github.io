---
layout: post
title: "Ruby call chain"
description: ""
category: 
tags: []
---

Ruby has several interesting mechanics in their OO system.
First, we have plain old inheritance. Like most OO systems, this allows our methods to keep
DRY by centralizing common methods among classes.
Next, ruby has mixins which allow us to dynamically inject snippets of code into classes.
Lastly, we have method_missing, which gives classes a chance to dynamically program itself.

In OO systems, objects invoking methods first check the current class, and move up the 
inheritance chain until a matching method is found, else `some_exception` is thrown.

With mixins included, Ruby first checks for mixins binded to the current class, in the most
recently mixed-in order before proceeding to the parent class. For example, the following hierarchy:

    class B < A
        include X
        include Y
    end

would attempt to find the method name in this order.

1. B
3. Y
3. X
4. A

todo: What if we have nested mixins? 

### Method Missing
Method missing is interesting bec

# Gotchas with method_missing
