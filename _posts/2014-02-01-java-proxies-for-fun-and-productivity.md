---
layout: post
title: "Dynamic Proxies for Fun and Productivity"
date: 2014-02-01
comments: true
tags: Java AOP dry proxy
---

Dynamic proxies are one way of making reusing code by adding a layer of indirection. It provides the building blocks for the behaviors of frameworks which you may have seen like Spring MVC.

In this post, we'll build a proxy which validates method preconditions by validating method parameters. This allows us to write DRY code.

### Background
When I first saw Java annotations for the first time, I had no idea what was going on. What are these annotations? Why are their definitions empty? How do they get behavior? It boggled me and I didn't what was going on. Unfortunately, using a complex framework like __Spring MVC__, it's difficult for beginners/intermediate developers to grasp all the different aspects to make these bits useful.

Let's say we want to fetch some posts from a bucket with a given date range. The `bucketID` parameter needs to be a certain format (validated by regex), and the DateRange is a data structure containing our search parameters. This is what we want to avoid in methods.

```java

public getPosts(String bucketId, DateRange range) {
    if (postId == null) throw new BucketException("bucketId cannot be null")
    if (range == null)  throw new BucketException("range cannot be null")
    try { // potentially this could be more complex like do regex matching.
        Long.parseLong(bucketId)
    } catch (NumberFormatException e) {
        throw new BucketException("bucketId has invalid format")
    }

    // rest of code...
}

```

The worst part of this is now we need to repeat this for __every__ API we want to expose in this service... you can imagine `getPost`, `createPost`, `deletePost`, `editPost`. After a while, this gets repetitive... so how would we get this to work.

We want to push this responsibility elsewhere because it's clearly reusable. This is what we want in the end.

```java
public getPosts(@PostId String bucketId, @NotNull DateRange range) {
    // rest of code...
}
```

This is short, succinct -- you can put this in your interface so it's reused across implementations. It's also easier on the eyes than the noise generated from checking pre-conditions. So, how do do this? Let's start with the annotations.

### Annotations
Java annotations are simply meta data. That's it. No behavior is stored in the definition of the meta data, all it does is mark it's component. Annotations can allow other parts of the program to check for presence of specific annotations to change runtime behavior.

From above, we need a `PostId` annotation and `NotNull` annotation. These are markers for other pieces of the program to read and consume perhaps.

```java
// In PostId.java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
@Inherited
public @interface @PostId {}
```

```java
// In NotNull.java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
@Inherited
public @interface @NotNull {}
```

The definition of the annotation can be looked up elsewhere -- but notice the annotations on these annotations (meta!). `@Retention(RetentionPolicy.RUNTIME)` is required because we be parsing annotations during runtime.`@Target({ElementType.PARAMETER})` is to enable us to put the annotations on method parameters. `@Inherited` is useful if you want to include the annotation.


### The Dynamic Proxy
```java
public interface PostRepository {
    public getPosts(@PostId String bucketId, @NotNull DateRange range);
}
```

```java
public PostRepositoryImpl implements PostRepository { 

    public getPosts(String bucketId, DateRange range) {
        // some implementation
        // note: this does not need to have annotations attached but could.
    }
}
```

Now we attach the annotations to an interface. The code above is perfectly legal but it won't actually do the post checking behavior for us. What we need is to introduce some mechanism to parse the annotations and create our new behavior. This can be implemented using dynamic proxies.

Dynamic proxies consist of two components: an `InvocationHandler` which looks for annotations and adds validation checks, and a proxy object which provides a clean interface for `InvocationHandler`

##### Creating an Invocation Handler
Control of a method's execution is done with a `InvocationHandler` (see Javadoc). A brain-dead implementation would call the method itself like so. In our brain-dead example below -- generics are used so this functionality can be used on any type.

```java
public class ValidationHandler<T> implements InvocationHandler {

    T delegated;

    public ValidationHandler(T delegated) {
        this.delegated = delegated;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // brain dead implementation, works like:
        // proxy.method(arg1, arg2, arg3, ...)
        return method.invoke(delegated, args);
    }
}
```

Our ValidationHandler needs a reference to the object we want to delegate the work to. Because we're using generics this can be any object which is pretty sweet.

Classes that implement the `InvocationHandler` interface __must__ override the invoke method. It takes three parameters: proxy, method, args. These variables represent the method call which is similar to the statement

```java
    proxy.method(args[0], args[1], args[2], ...)
```


##### Adding Custom Behavior Before and After
Notice how we have control over when `method.invoke` is called. This gives us flexibility to do behavior before or after `method.invoke`. Our intention is to scan for the presence of `@PostId` and `@NotNull` annotations and then perform some validation if it exists. So, we'll update the `invoke()` method as follows.

```java
public class ValidationHandler<T> implements InvocationHandler {

    // ... constructor omitted and delegated omitted

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Method originalMethod = delegated.getClass().getMethod(method.getName(), method.getParameterTypes());

        Annotation[][] annotations = originalMethod.getParameterAnnotations();
        for (int i = 0; i < annotations.length; i++) {
            for (Annotation an : annotations[i]) {
                if (an.annotationType().equals(NotNull.class)) {

                    // perform CheckNull, perhaps throw an exception?
                    if (args[i] == null) throw BucketException("Input cannot be null")

                } else if (an.annotationType().equals(PostId.class)) {

                    // Check Name of Post, should be numerical value only
                    try {
                        Long.parseLong((String) args[i])
                    } catch (NumberFormatException ex) {
                        throw new BucketException("bucketId has invalid format")
                    }
                }
            }
        }

        try {
            return method.invoke(delegated, args);
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }
    }
}
```

Now this is where it gets interesting. The flow of `invoke()` is like so:

1. Get the method definition of the delegated class which has the same signature as the invoked method
2. Find the annotations for each parameter in the delegated objects method
3. Iterate through the annotations. If the `NotNull` or `PostId` annotation is present, perform a check which will throw exceptions if it does not pass.
4. Invoke the method on the delegated class if we pass the checks.

This flow is fairly straightforward. This is a very powerful mechanism that allows us to basically build behavior that happens before and after a method is called. It doesn't have to be annotation parsing per se.

##### Using an Invocation Handler

Now that we have all the functionality wrapped up in `ValidationHandler`, here's an example of how to use it with a theoretical `PostRepository` interface.

```java
public static void main(String[] args) {
    // can be anything
    PostRepository repository = new PostRepositoryImpl();

    ValidationHandler handler = new ValidationHandler(repository);

    // get that method from our class
    Method method = repository.getClass().getMethod("getPosts", {String.class, DateRange.class})
    handler.invoke(handler, method, ["15", new DateRange()])
}
```

It's not particularly nice to work with because of all the boilerplate. We have to do something very un-java like by by using reflection directly to find a method with that signature. Lastly, the result of this operation is not type safe.

### Wrapping things up with a Dynamic Proxy
Raw invocation handlers do not provide enough abstraction for regular use. What we really want is to make a ValidationHandler appear as a `PostRepository`. This way, other parts of the application can reuse a PostRepository without needing to know the details of how the annotations are handled.

This is where Dynamic proxies come in. Dynamic proxies pretend to be implementations of of interfaces and instead delegate the actual work to a `InvocationHandler`. This pattern allows us to build and compose objects out of reusable components which cannot generally be reused otherwise (such as this post).

The gist of what we need to do is to create a factory that creates a proxy of `PostRepository` that uses the `InvocationHandler` called `ValidationHandler`. We implement this as a static method on `ValidationHandler`

```java
public class `ValidationHandler`

Proxy.newProxyInstance(cl,
                delegate.getClass().getInterfaces(),
                new ServiceValidationHandler<>(delegate));

```


Dynamic proxies are objects which delegate their work to actual implementations. It behaves function decorators if you come from a Python background. It intercept method calls allowing developers to add custom behaviors before and after invoking the method. We first need to create an instance of a proxy, and then override it's default behavior to parse our annotation.

##### Create a Dynamic Proxy
Creating a dynamic proxy is simply done by using a Factory method to construct an instance of it. The code looks like the following. Our dynamic proxy maker will be called `ValidationInvoker`.

```java
public class ValidationHandler<T> implements InvocationHandler {
    // rest of code omitted for brevity
    public static <T> T newInstance(T delegate) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return (T) Proxy.newProxyInstance(cl,
                delegate.getClass().getInterfaces(),
                new ValidationHandler<>(delegate));
    }
}
```

This method takes an instance of T, our `delegate`, whose methods we want to intercept. From it's interfaces, we build a proxy that delegates the work to an instance of 
`ValidationHandler`. We cast the proxy back to type T, which is perfect because it hides all the implementation details of the proxy. i.e. Passing a `PostRepository` in will return back a `PostRepository`.

The only gotcha here is it only works if `T` comes from an __interface__. Proxies can only mock interfaces, therefore `newInstance` will throw an exception if `T` is a class.

### Run it
A snippet of how this works below. (Untested)

```java
public static void main(String[] args) {

    // newInstance returns 
    PostRepository repository =  ValidationHandler.newInstance(new PostRepositoryImpl());
    repository.getPosts("*@#()", new DateRange())
    // => throws exception: BucketException("bucketId has invalid format")

    repository.getPosts("12345", new DateRange())
    // => works
}
```

### Closing Thoughts
This is a very powerful feature and highly reusable. You can easily wire up these annotations on other methods on your classes and they'll automatically get this pre-condition checking. It is very terse, and keeps DRY.

ValidationHandler is also really cool because it transparently builds classes. It basically wraps over an instance of any interface and spits the same type out which you can hand off to other parts of the application, i.e. with Dependency Injection. You can take this one step further by making the dependency injection tools build proxies for you. Spring Framework's AOP basically does this for you. It creates dynamic proxies in the background to do AOP. It also uses this to model complex components like RMI,connecting to external services with Spring Integration, controller input validation, and transaction management.

There are some downsides though. Dynamic proxies introduces indirection in the code, may cause slight performance impacts, and are generally used sparingly unless you're a framework developer but it can definitely be useful.

For my own work, I've been separating out reusable backend components with Apache Thrift so we cannot and do not want to introduce the complexities tied to Java Web Frameworks. Instead, we can pick out the components we like from these frameworks and build it ourselves, such as  `ValidationHandler`. It is specific to our domain and also reusable.

### Things that can be improved
I cannot comment on how `ValidationHandler` would work as we add more and more types. The type checking right now is really trivial and uses `if/elseif/else`. As more validations are added, a different pattern has to be used, perhaps like Hibernate Validators.

###### Source Code
_Will make working example code if people are interested._

Feel free to message me if you have any thoughts, suggestions or questions in this post.


