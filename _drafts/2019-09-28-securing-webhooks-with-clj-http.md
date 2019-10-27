---
title: Productionizing webhooks (with clj-http)
layout: post
tags: clojure http webhooks
---

This is a distilled (living) checklist of things to think about for
productionizing webhooks (with clj-http in mind). The document is broken into
three sections.

1. Background
2. Configuring for Production Use
3. Security Considerations

## Background

Webhooks are "user-defined HTTP callbacks". Consumers of webhooks sets up an
endpoint that our server sends HTTP requests to. This sends data to 3rd party
services in real time (in the case of B2B, getting data from one system to
another).

`clj-http` is data-oriented, i.e. the request/response are hash-map data
structures which is easy to manipulate. It has a simple/powerful middleware
layer, which makes it easy to layer on cross-cutting customizations.

## Configuring for Production

1. Explicitly handle HTTP Response codes on a case-by-case basis.

For `clj-http`, set `:throw-exceptions? false`. Webhooks work at the HTTP level,
and your application *may* choose to handle 4xx and 5xx responses differently.
For example, if a serrver responds with 4xx, you probably don't want to retry.
If a server responds with 5xx, you *may* want to retry the request later.

2. Configure connection pooling (if it makes sense)

`clj-http` has support for connection pooling to speed up sending repeated
requests to the same endpoint. Keep in mind that this *only* speeds up requests
made to the same host, so you'll have to double check.

3. Configure Retries

The underlying Apache HTTP Client already does this out of the box it encounters
recoverable exceptions. However, you *may* customize it if needed.

4. Configure all the timeouts

Your production webhook system should always have timeouts set to avoid starving
resources. For productionizing `clj-http`, you should set these values.

  :socket-timeout
  :conn-timeout / :connection-timeout
  :conn-request-timeout

5. Configure Redirect Behaviour

You sould decide up front how your webhooks system interacts with `redirects`.
In `clj-http`, there are several strategies with configurable limits. I
recommend using `:graceful` because it will allow you to handle `3xx` response
codes. See point 1.

# Security Considerations

1. Change/Hide your user-agent

By default, all requests will include the header like this: `User-Agent:
Apache-HttpClient/4.5.3 (Java/1.8.0_102)`. You should disable it, or set it
explicitly in `clj-http` to be something more meaningful to your webhook system.

2. Prevent invalidated host attacks. (See OWASP [Cheatsheet](https://cheatsheetseries.owasp.org/cheatsheets/Unvalidated_Redirects_and_Forwards_Cheat_Sheet.html))

Implement a whitelist or blacklist to prevent requests by checking Host IPs. 

For example, if you run your production webhook system on AWS EC2, you should
blacklist webhooks that request and/or redirect to 169.254.169.254.



For example, setup a blacklist to prevent requests made to your private
network.

atOpen Redirect Attacks

Attackers can 

You should have a blacklist setup to prevent webhooks from making requests to
your internal network. Use 

Blacklist HFilter out hosts

Implement this using middlewares (why? because when you're testing locally, you're going to need to do funny things)

3. Blcoking redirects to internal infrastructure

4. setup a proxy server with a stable ip (use proxy host/port/user/pass)

# Logging/Storing Webhook Request/Responses

1. Filter out sensitive headers: authorization
2. Capture, filtering, limiting response size
3. filtering out sensitive data for logs
4. logging, and not consuming the stream
5. avoiding parsing the response body
