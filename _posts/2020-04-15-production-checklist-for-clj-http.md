---
title: Production Checklist for clj-http
layout: post
tags: clojure current-practise
---

This is a distilled (living) list of considerations when productionizing a
system using [clj-http]. 

[clj-http]: https://github.com/dakrone/clj-http

The document is broken into several sections.

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [Background](#background)
- [Configuring for Production](#configuring-for-production)
- [Security Considerations](#security-considerations)
- [Logging & Storing Request/Response](#logging--storing-requestresponse)

<!-- markdown-toc end -->

## Background

The recommendations in this document are lessons extracted from building a
webhook delivery system using `clj-http`. The checklist in this document assume
that you *may not* know or trust the destination you're communicating with.

Keep that in mind, because some of these recommendations may not be suitable for *your* use-case.

## Configuring for Production

### 1. Handle HTTP Response Status using regular control flow.

For outbound requests, set `:throw-exceptions? false`. If your application
handles 4xx and 5xx responses using regular control flow, it will be easier to
test & maintain. Depending on the endpoint you interact with, you may want
different behaviors for handling 4xx and 5xx responses.

If you do want to bubble up with an exception, throw exceptions fromfrom your
application code, rather than asking `clj-http` to do it.

### 2. Configure connection pooling (if it makes sense)

`clj-http` has support for connection pooling to speed up sending repeated
requests to the same endpoint. Keep in mind that this *only* speeds up requests
made to the same host, so you'll have to know your use-case.

### 3. Be aware of automatic retries

This is enabled by default in `clj-http`. This is customizable using the key `:retry-handler`.

The underlying Apache HTTP Client will retry on IOExceptions by default. You
should be aware of how this behaves. See [Apache HTTP Client Section 1.5.3:
Automatic Exception
Recovery](https://hc.apache.org/httpcomponents-client-ga/tutorial/html/fundamentals.html#d5e305).

### 4. Set all socket & connection timeouts

Your production webhook system should always have timeouts set to avoid starving
resources. For productionizing `clj-http`, you should set these values.

- `:socket-timeout`
- `:conn-timeout` or `:connection-timeout`
- `:conn-request-timeout`

If unset, these values default to `0` whose behavior is to disable the timeout.

### 5. Configure an end-to-end deadline

The `clj-http` timeouts do not work for enforcing an end to end timeout policy.
You will need to handle this at your application level, i.e. using futures.

A malicious or poorly behaving server may slowly drip-feed bytes back, just
enough to stay below the `:socket-timeout` value. Your system should be
resilient to slow-drip feeders.

Here's a code snippet that illustrates this. In this demo, a server is
configured to produce data every 100 milliseconds. The client code makes a
request that asks the server to feed data 10 times. The total request time
should be around 1 second. Various values of `:socket-timeout` are tested.

```clojure
;; start repl with dependencies using this command:
;;
;;   clj -Sdeps '{:deps {clj-http {:mvn/version "3.10.0"} aleph {:mvn/version "0.4.6"}}}
;;
(require '[clj-http.client :as http]
         '[clojure.string :as str]
         '[aleph.http]
         '[manifold.stream :as s])

(defn handler
  "Slow handler that returns data once every 100 milliseconds

  Taken from https://aleph.io/aleph/literate.html#aleph.examples.http"
  [req]
  (let [cnt (Integer/parseInt (second (str/split (get req :query-string) #"=")))]
    {:status 200
     :headers {"content-type" "text/plain"}
     :body (let [sent (atom 0)]
             (->> (s/periodically 100 #(str (swap! sent inc) "\n"))
                  (s/transform (take cnt))))}))

(defonce server
  (aleph.http/start-server #'handler {:port 8080}))

(comment
  ;; throws SocketTimeoutException
  (println (http/get "http://localhost:8080?count=10" {:socket-timeout 99}))

  ;; throw SocketTimeoutException
  (println (http/get "http://localhost:8080?count=10" {:socket-timeout 100}))

  ;; success, total request time is ~1 second, or 10x the socket-timeout value
  (println (http/get "http://localhost:8080?count=10" {:socket-timeout 105})))
```

### 5. Configure Redirect Behaviour

You sould decide up front how your webhooks system interacts with `redirects`.
In `clj-http`, there are several strategies with configurable limits. I
recommend using `:graceful` because it will allow you to handle `3xx` response
codes. See point 1.

### 6. Do not parse the response body

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
