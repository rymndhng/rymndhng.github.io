---
layout: post
title: "Documenting a Rails API using swagger"
category: rails
tags: []
---
Working in an environment where you may have different backend/frontend
developers is really difficult unless your developers are co-located,
especially if your frontend developers are unfamiliar with your stack. Let's
say your backend is done with a REST API. How would you communicate the layout
to your frontend developers that don't do Ruby/Rails for example. They speak
iOS and Objective-C.

For a rails developer, the simplest we can do is dump `rake routes` to a file
and make that available. But that is not enough. Clearly, if I have exposed a
RESTful resource, we know how `GET/POST/PUT/DELETE` a resource. You still have
no idea what the fields are though. Does a user have a *name* field? Probably.
What about something a little more esoteric like *dob* or *country*. A bit
more documentation would be helpful here wouldn't it.

I like structure. I'm sure other Programmers like structure as well. We use
rake to automate repetitive tasks, and we use capistrano to remote deploy. We
should be doing the same thing for documenting our APIs. Hint: there's a gem
for that.

### Swagger-Docs (for Rails)
Yes, there's a gem for that. Check it out here: https://github.com/richhollis/swagger-docs.
The format is simple and you stick it inline your controller just like how you
document your code.

In fact, I find this much more valuable to document APIs because it explicitly
specifies the contract between the backend/frontend developers. Here's an
example taken from the sample documentation.

```ruby
swagger_api :create do
  summary "Creates a new User"
  param :form, :first_name, :string, :required, "First name"
  param :form, :last_name, :string, :required, "Last name"
  param :form, :email, :string, :required, "Email address"
  response :unauthorized
  response :not_acceptable
end
```

The swagger-docs DSL is concise. It benefits the backend developer too because
I can't track of all the params. They're generally not very explicit.

