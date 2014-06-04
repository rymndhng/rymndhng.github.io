---
layout: post
title: "Don't fight with Spring for Config"
date: 2013-08-02 08:52
comments: true
categories: 
---

Although this seldoms happen in a web project, occasionally you want to setup a 
configuration file so that different deployments will have different features
active. Ofcourse, this configuration file will touch several different components,
so the configuration has to be code agnostic. So far, I've found that placing
this inside `app.properties` file seems to be a clean way to get about it.

This is a common situation in Spring-based projects, and they have a way of
injecting app.properties into XML configuration using using SpEL (Spring 
Expression Language), which is all fine and dandy. All you have to do is wire 
the `*.properties` files within a `PropertyPlaceholderConfigurer`, as below

```
file: app.properties

app.billing.path=/var/log/proj/
app.billing.enable=false
```

```
<bean id="properties"
  class="org.springframework.beans.factory.config.PropertyPlaceholderConfigurer">
  <property name="locations">
    <list>
      <value>classpath:app.properties</value>
      <value>classpath:jdbc.properties</value>
    </list>
  </property>
</bean>
```

Now you can reference it in other spring-based XML configurations like so using
templating with `${value}`

```
<int-file:outbound-channel-adapter
  channel="billingLogWriteChannel-2" mode="APPEND"
  directory-expression="'${app.billing.path}'+headers.dir" />
```

This is all fine and dandy, but how about doing this programatically (in Code).
This is where we hit a big roadblock. The `PropertyPlaceholderConfigurer` bean
is not meant for consumption in your code. The class's useful methods are marked
`protected`, so what do you do?

In my situation, my ServletFilter needed the second configuration 
`app.billing.enable` but because it's technically outside Spring's beans, you 
cannot inject the value using SpEL, like you would with beans. The `<filters>` 
can have initialization parameters but once again, you can't use SpEL.

### Solutions
Several approaches are possible ofcourse:
1. Read the app.properties into a `Java.util.properties`
  - This approach is simple, but it exposes the filesystem, and now you have
  parts interacting. Furthermore, now we have multiple ways to access 
  `*.properties` which is not DRY.
2. Subclass `PropertyPlaceholderConfigurer` and expose the methods publically.
  - Don't know if we're opening a can of worms. I don't know how reuseable this
  code will be, and I want to avoid modifying Spring's source for a specific use
  case.
3. Use @Value("${app.billing.path}") annotations.
  - This didn't work for my filter, and I'm not a fan of too much magic. What I 
  dislike about the `@Value` annotation is that it is used in multiple contexts.
  When we can avoid magic, we should.
4. Create a POJO configuration and inject beans into it
  - Verbose, but allows you to follow Spring's convention of XML based injection.

In the end, I used method #4 because it was the simplest to understand, despite 
having to build a POJO class to store the configuration. Spring does not seem to
have a way to programmatically access `PropertyPlaceholderConfigurer` as a Map
structure, like what you'd expect in other languages.

My impression of Spring is that it goes a lot of the way to isolate code, mainly
objects from their operating environment. You shouldn't be reading files from the
filesystem because then there's a separation of concern. Users should only think
of working with Java Objects, let the rest of the system handle the mapping.

The reality is, you will have to deal with Filesystems, reading files,
serializing/deserializing. Having all these XML-based configurations and magic
annotations increases indirection, and difficulty in debugging (such as #3 and #4).
Because of this, it's extremely difficult to program without an IDE with intellisense.
You really don't know what's going on with external jar files. Contrast this to
dynamic languages such as Ruby/Python where you can insert a breakpoint in-code 
by invoking `binding.pry()` or `pdb.set_trace()` and start playing with the REPL
to test your code. In the case of Java, sometimes I wonder if my SpEL is working,
how do I know the magic annotation is reading it wrong. What really sucks is that
for @Value annotations to work, you really need to set your code up a certain way.
i.e. it infers it from Getter/Setter methods. And all I want to ask is why?
