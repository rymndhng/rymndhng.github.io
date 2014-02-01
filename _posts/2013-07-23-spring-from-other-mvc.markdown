---
layout: post
title: "Spring from other MVC"
date: 2013-07-23 14:33
comments: true
categories: 
---

I've done CodeIgniter, RoR, Java/Play!, Scala/Play! and contrast to Spring, it's
a different paradigm from those other web frameworks. Spring is like a heavily
moddable car, you can swap out anything, whereas the ones mentioned before are
opinionated in how things are done.

When working with web stacks, I'm interested in seeing the overall flow between
the layers. So for example, going from up->down, we see URL Routing -> Controller
-> Binding -> process request -> return something. I find the Spring documentation
inadequeate in this regard to help newbies get in. The documentation jumps over
the place and it's difficult to know where to start. Videos aren't always useful because half of them are out of date/refer to another version.

The magic of Spring should help get people started, but for developers jumping onto
an existing project, it's a huge case of what the hell is going on.

Springs MVC

  @RequestMapping(value = "/new", method = RequestMethod.POST)
  public String create(@ModelAttribute("widget") @Valid WidgetForm form,
      BindingResult result,
      @RequestParam(required = false) String redirect, Model model) {

The first line speaks for itself quite well. The next 3... is a whole mish mash
of things going on, from the learning perspective.

Given this is in a controller, I expect to follow up with what @Model
