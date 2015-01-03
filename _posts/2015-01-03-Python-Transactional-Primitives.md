---
layout: post
title: "Simple Python Transaction Primitives"
description: "Implementing a simple transaction like DSL for Python."
category: python
comments: true
tags: [python intermediate]
---

In this exercise, I tried to implement transaction/rollback constructs using
Python Decorators and Context Managers.

## Problem To Solve
When you make a stateful change to the world -- you want to undo your change in
the case of failure. When you start trying to build complex & composable
operations, for example in AWS, you may do this:

1. spin up an instance
2. attach EBS
3. give it an elastic IP
4. give it a public DNS

Well, if it fails, you want to undo all the operations. The AWS API is not transactional, how can we make this rollback manageable?

```python
import boto.ec2
conn = boto.ec2.connect_to_region('us-east-1')
try:
    instance = conn.run_instances('ami-fe147796')    # Start an instance
    throw Exception("testing rollback")
except:
    instance.terminate()                             # Kill an instance
```

## Motivation
I wanted to come up with a minimal abstraction to make these operations
safer. Some flaws I identified with the above approach:

- ordering, rollback should happen in reverse order, and with the above approach we expect the developer to remember this
- if you work across multiple files or many rollbacking operations, you lose track how to safely undo things

With my approach, you no longer have to worry about these concerns.

We would like to having nice constructs for rollbacking database operations, but
we don't for other stateful operations, like working with boto's AWS
API. Django[^1] has such feature. With Django's transactions, we can easily
rollback changes to many tables at once. These operations are 'atomic' -- either all are successful or none are.

In Django, you can write transactional code like so: [^2]

```python
from django.db import transaction

def viewfunc(request):
    # This code executes in autocommit mode (Django's default).
    do_stuff()

    with transaction.atomic():
        # This code executes inside a transaction.
        do_more_stuff()
```

We don't get it as nice as Django, but I want to apply the same technique to
wrap over Boto's AWS API. This is the end goal:

```python
import boto.ec2

conn = boto.ec2.connect_to_region('us-east-1')
with RollbackManager():
    # This rolls back the AWS commands if it fails
    run_instances(conn, 'ami-fe147796')
    throw Exception("testing rollback")
```

## Usage
To use these abstractions, you need to define Rollback-able Operations. I
implemented a Python Decorator `@Rollback` to link two functions together. We
also need a Context Manager to define the boundaries of a transaction for
rollbacking.

Before we start, we should define some terminology:

--------------------

**Stateful Operation:** A function that causes permanent change in the
    world. In this case, we're using the AWS API to spin up a new instance

**Rollback Operation:** The reverse stateful operation to undo a stateful
   operation. i.e. if I create a new AWS instance, the rollback should be to
   delete the instance.

--------------------

### Creating a Rollback-able Operation

Define a pair of functions which consists of the Stateful Operation, and the
Rollback Operation. The rollback operation links to the stateful operation with
the `@Rollback` decorator.

```python
# Regular python function to delete something
def terminate_instance(instance, fn_args):
    if instance:
        instance.terminate()

# Regular python function to create something -- with a special @Rollback
@Rollback(terminate_instance)
def run_instances(conn, **kwargs):
    reservation = conn.run_instances(**kwargs):
    instance, = reservation.instances
    return instance
```

The `@Rollback` decorator takes a function for rollbacking when an exception is
thrown. The rollback function takes two arguments. The first is the **return
value** of a stateful operation. The second is the **arguments** passed into the
stateful operation. This _should_ provide your rollback function with enough
context to undo.


### Rolling Back
To rollback, we need to decide which operations are all-or-nothing. This is most
naturally expressed in Python with a [Context Manager][context-manager]. The
context manager defines a boundary in which our RollbackManager has effect.

```python
import boto.ec2
conn = boto.ec2.connect_to_region('us-east-1')

with RollbackManager():
    run_instances(conn, 'ami-fe147796')
    run_instances(conn, 'ami-fe147796')
    run_instances(conn, 'ami-fe147796')
    run_instances(conn, 'ami-fe147796')

    # This exception will cause all 4 operations to rollback in reverse order
    throw Exception("testing rollback")
```


## Code & Etc

See [this code][code].

I took some inspiration from Java's Spring Framework as well.


[^1]: https://docs.djangoproject.com/en/1.7/topics/db/transactions/

[^2]: https://docs.djangoproject.com/en/1.7/topics/db/transactions/

[code]: https://gist.github.com/rymndhng/07cb0e626b7e7adc4f5e/edit "Simple Transaction Gist"

[context-manager]: https://docs.python.org/2/library/contextlib.html
