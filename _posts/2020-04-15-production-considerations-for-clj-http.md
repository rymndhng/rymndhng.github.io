---
title: Production Considerations for clj-http
layout: post
tags: clojure current-practise
---

This list contains lessons learned from building a webhook service using
[clj-http].  

[clj-http]: https://github.com/dakrone/clj-http

## Considerations

### 1. Handle HTTP Response Status using regular control flow

For outbound requests, set `:throw-exceptions? false`. 

From my experience, handling 4xx and 5xx responses with regular control flow is
easier to test and maintain. [^1]

### 2. Configure Connection Pooling (if it makes sense)

`clj-http` has support for connection pooling to speed up sending repeated
requests to the same endpoint. Keep in mind that this *only* speeds up requests
made to the same host, so you'll have to know your use-case.

### 3. Beware of automatic retries

By default, `clj-http` retries all IOExceptions. To turn this off, set
`:retry-handler` to nil. See [Apache HTTP Client Section 1.5.3: Automatic
Exception
Recovery](https://hc.apache.org/httpcomponents-client-ga/tutorial/html/fundamentals.html#d5e305).

### 4. Set timeouts

These should always be set to non-zero values to avoid blocking indefinitely.

- `:socket-timeout`
- `:conn-timeout` or `:connection-timeout`
- `:conn-request-timeout`

### 5. Configure end-to-end timeout

The timeouts in [4] do not enforce an end-to-end timeout. You will need to
handle this in your application code.

A malicious or poorly behaving host may cause a Denial of Service by sending
bytes slowly, staying below the `:socket-timeout` value.

Here is a code snippet that illustrates that `:socket-timeout` is insufficient.
The server produces data every 100 milliseconds up to 1 full second. The client
changes the value of `:socket-timeout` to show different scenarios. 

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

The default strategy will throw an exception when the redirect count exceeds the
max redirects. 

Prefer using `:graceful` and handling `3xx` responses with normal control flow. See [#1](#1.-Handle-HTTP-Response-Status-using-regular-control-flow).

### 6. Beware of Body Coercion

> This applies specifically to 3rd parties you do not trust

Do not trust external systems to negotiate response formats correctly. Coercing
invalid JSON, EDN or other structured formats may cause unexpected exceptions.
Prefer capturing data using binary or text formats.

See [clj-http#output-coercion](https://github.com/dakrone/clj-http#output-coercion).

### 7. Change or Hide Your User Agent

By default, all requests will include the header: `User-Agent:
Apache-HttpClient/4.5.3 (Java/1.8.0_102)`. Disable it, or set it explicitly in
`clj-http` to be something more meaningful to your system.

### 8. Validate Hosts

> This applies specifically to 3rd parties you do not trust 

See OWAP Cheatsheet (See OWASP
[Cheatsheet](https://cheatsheetseries.owasp.org/cheatsheets/Unvalidated_Redirects_and_Forwards_Cheat_Sheet.html)).

If your application runs on public clouds, you should block access to the
internal network. As an example, on AWS, the EC2 Metadata Address
169.254.169.254 may reveal information about the machine.

Implement Host validation as a clj-http middleware, and also as a custom
`:redirect-strategy` to check for valid IPs.

See [clj-http#custom-middleware](https://github.com/dakrone/clj-http#custom-middleware).

See this snippet as a starting point for filtering IPs:

``` clojure
(defn allowed-host?
  [^java.net.InetAddress inet-addr]
  (try
    (not (or (.isLinkLocalAddress inet-addr)   ; 169.0.0.0/8
             (.isLoopbackAddress inet-addr)    ; 127.0.0.1
             (.isAnyLocalAddress inet-addr)    ; 0.0.0.0
             (.isMulticastAddress inet-addr)   ; 224.0.0.0 to 239.255.255.255
             (.isSiteLocalAddress inet-addr))) ; 10.0.0.0/8, 192.168.0.0/16
    (catch Exception e
      false)))
```

### 10. Forward Requests through a Proxy Server

Use this when 3rd parties prefer to receive requests from an IP Allowlist. See
[clj-http#proxies](https://github.com/dakrone/clj-http#proxies).

### 11. Sanitize Logged Data

If your service logs or stores HTTP request/responses, consider:

1. Filtering out sensitive data, i.e. `Authorization`
2. Truncating the request/response body to a max length

[^1]: Effective Java advocates "Use Excetions only for exceptional conditions". See https://www.oreilly.com/library/view/effective-java-2nd/9780137150021/ch09.html
