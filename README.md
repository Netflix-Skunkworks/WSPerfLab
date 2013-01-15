# WSPerfLab
=========

Project for testing web-service implementations.

The test setup will consist of the following:

<img src="https://raw.github.com/wiki/benjchristensen/WSPerfLab/images/overview.png" width="860" height="300">

The <a href="WSPerfLab/tree/master/ws-backend-simulation">ws-backend-simulation</a> is a simple Java backend app accepting request arguments to affect its response size and latency.

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

- server-side end-to-end latency as returned in response headers
- client-side end-to-end latency for entire trip including network
- response payload size


# Request Use Case

An HTTP GET will request /test which is expected to perform the following:

- A) GET http://hostname:port/mock.json?numItems=2&itemSize=50&delay=50
- B) GET http://hostname:port/mock.json?numItems=25&itemSize=30&delay=90
- C) GET http://hostname:port/mock.json?numItems=1&itemSize=5000&delay=1
- D) GET http://hostname:port/mock.json?numItems=1&itemSize=1000&delay=1
- E) GET http://hostname:port/mock.json?numItems=100&itemSize=30&delay=150



The expected response will look like this (without pretty print):

```json
{
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


# Statistics and Report

