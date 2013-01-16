# WSPerfLab
=========

Project for testing web-service implementations.

The intent of the test design is to simulate behavior typical of real-world web service applications.

This includes things such as:

- parallel network execution to backend services
- multiple (5 in this case) backend services
- conditional request flows (C and D require data from A, E requires data from B)
- JSON deserialization and serialization
- work such as math, iteration, string manipulation (not just proxying a stream of bytes)

# Structure

The test setup will consist of the following:

<img src="https://raw.github.com/wiki/benjchristensen/WSPerfLab/images/overview.png" width="860" height="300">

The <a href="WSPerfLab/tree/master/ws-backend-mock">ws-backend-mock</a> is a simple Java backend app accepting request arguments to affect its response size and latency.

The various test implementations are intended to each implement the same logic with different architectures, languages and frameworks.

- <a href="WSPerfLab/tree/master/ws-java-servlet-blocking">ws-java-servlet-blocking</a>
- <a href="WSPerfLab/tree/master/ws-java-servlet-nonblocking">ws-java-servlet-nonblocking</a>
- <a href="WSPerfLab/tree/master/ws-java-vertx">ws-java-vertx</a>
- <a href="WSPerfLab/tree/master/ws-java-netty">ws-java-netty</a>
- <a href="WSPerfLab/tree/master/ws-clojure-noir">ws-clojure-noir</a>
- <a href="WSPerfLab/tree/master/ws-clojure-vertx">ws-clojure-vertx</a>
- <a href="WSPerfLab/tree/master/ws-nodejs">ws-nodejs</a>
- <a href="WSPerfLab/tree/master/ws-python-gevent">ws-python-gevent</a>


The <a href="WSPerfLab/tree/master/ws-client">ws-client</a> will drive the traffic and capture performance metrics.

Metrics to be captured are:

- client-side end-to-end latency for entire trip including network
- response payload size


# Request Use Case: A

An HTTP GET will request /test?id={uuid} which is expected to perform the following:

- A) GET http://hostname:port/mock.json?numItems=2&itemSize=50&delay=50&id={uuid}
- B) GET http://hostname:port/mock.json?numItems=25&itemSize=30&delay=150&id={uuid}
- C) GET http://hostname:port/mock.json?numItems=1&itemSize=5000&delay=80&id={a.responseKey}
- D) GET http://hostname:port/mock.json?numItems=1&itemSize=1000&delay=1&id={a.responseKey}
- E) GET http://hostname:port/mock.json?numItems=100&itemSize=30&delay=4&id={b.responseKey}

The conditional flow of requests is demonstrated in this diagram:

<img src="https://raw.github.com/wiki/benjchristensen/WSPerfLab/images/requests.png" width="860" height="260">

The JSON response will include a 'responseKey' value which is generated via:

```
c.responseKey * d.responseKey * e.responseKey
```

The expected response will look like this (without pretty print):

```json
{
  "responseKey":6502981934456555896, 
  "delay": [ { "a": 50 },{ "b": 90 },{ "c": 1 },{ "d": 1 },{ "e": 150 } ],
  "itemSize": [ { "a": 50 },{ "b": 30 },{ "c": 5000 },{ "d": 1000 },{ "e": 30 } ],
  "numItems": [ { "a": 2 },{ "b": 25 },{ "c": 1 },{ "d": 1 },{ "e": 100 } ],
  "items": [
    "Lorem ipsum dolor sit amet, consectetur adipisicin",
    "Lorem ipsum dolor sit amet, consectetur adipisicin",
    "Lorem ipsum dolor sit amet, co",
    "Lorem ipsum dolor sit amet, co",
    "... aggregate items from each of the 5 backend requests ...",
    "Lorem ipsum dolor sit amet, co"
  ]
}
```

# Validation

The 'responseKey' in the returned JSON will be asserted for correctness to validate that the 5 backend service requests were performed in the correct order.

JSON payload size and elements will also be checked for containing the expected results.

This will not be asserted on every invocation of ws-client during load testing, but will be a validation step done during development to confirm an implementation complies.


# Request Use Case: B

Less network, add ThreadLocal, ConcurrentHashMap, volatile, AtomicReference type behavior.

# Statistics and Report

