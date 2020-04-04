---
title: Production Checklist for Webhooks (with clj-http)
layout: post
tags: clojure http webhooks guide
---

This is a distilled (living) checklist of things to think about for
productionizing webhooks with clj-http in mind. The document is broken into
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

For outbound requests, set `:throw-exceptions? false`. Webhooks work at the HTTP
level, and your application *may* choose to handle 4xx and 5xx responses
differently. For example, if a serrver responds with 4xx, you probably don't
want to retry. If a server responds with 5xx, you *may* want to retry the
request later.

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

6. Do not parse the response body

Do not parse the response body if you can. You should not trust extenral servers
to negotiate content correctly. For `clj-http`, you should avoid using output coercion.

See [clj-http#output-coercion](https://github.com/dakrone/clj-http#output-coercion).

# Security Considerations

1. Change/Hide your user-agent

By default, all requests will include the header like this: `User-Agent:
Apache-HttpClient/4.5.3 (Java/1.8.0_102)`. You should disable it, or set it
explicitly in `clj-http` to be something more meaningful to your webhook system.

2. Prevent unvalidated host attacks

See OWAP Cheatsheet (See OWASP
[Cheatsheet](https://cheatsheetseries.owasp.org/cheatsheets/Unvalidated_Redirects_and_Forwards_Cheat_Sheet.html)).

This is a *very* important security consideration if your webhook system
captures the HTTP Request/Response. For example, if your webhook system runs on
AWS EC2, you should blacklist webhooks that request and/or redirect to
169.254.169.254.

The Host IP Address of the URL should be validated. Any redirects should also
have their Location's Host IP Address validated.

In `clj-http`, implement a custom middleware for validating the Host IP Address.

For readers, a simple IP blacklist function can be implemented with Java-interop. See
this example below:

``` clojure
(defn allowed-host?
  "Checks whether the given Inet Address for outbound request is allowed"
  [^java.net.InetAddress inet-addr]
  (try
    (not (or (.isLinkLocalAddress inet-addr) ; 169.0.0.0/8
             (.isLoopbackAddress inet-addr)  ; 127.0.0.1
             (.isAnyLocalAddress inet-addr)  ; 0.0.0.0
             (.isMulticastAddress inet-addr) ; 224.0.0.0 to 239.255.255.255
             (.isSiteLocalAddress inet-addr) ; 10.0.0.0/8, 192.168.0.0/16
             ))
    (catch Exception e
      false)))
```

See [clj-http#custom-middleware](https://github.com/dakrone/clj-http#custom-middleware).

3. Prevent unvalidated host attacks in redirects

This is an extension of point (2). Implement a custom `:redirect-strategy` to validate the redirect location. If the host fails validation, halt redirect execution.

4. Setup a Proxy Server with a Stable IP (use proxy host/port/user/pass)

Sophisticated customers who receive webhooks will want to whitelist IP
Addresses. Configure `clj-http` to make outbound requests through a proxy with
`:proxy-host`, `:proxy-port`. If the proxy is secured, also add `:proxy-user`
and `:proxy-pass`.

See [clj-http#proxies](https://github.com/dakrone/clj-http#proxies).

# Logging & Storing Request/Response

In your production system, you *may* want to log and/or store the webhook request responses. This is arguably the best part of working with clj-http. The request & response objects are "just data" which means its easy to serialize & deserialize.

To capture the request & response, I recommend adding a custom middleware at the bottom of the list. This approach allows you to capture the request *after* all `clj-http` middleware is applied and *before* any `clj-http` middleware is applied to the response.

This middleware should do the following things to avoid tampering with the request/response objects.

1. Append the captured request/response to the response with a namespaced keyword.

This is clojure. nuff said [rich hickey]

2. Filter out sensitive data

At minimum, the middleware should filter out the `Authorization` header. 

3. Reset the InputStream if you consume it

The response body is an InputStream. If you consume it, remember to reset it.

4. Limit the captured response body

With webhooks, you should limit the captured response body otherwise it could be used to exhaust your data store. 
