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

- <a href="WSPerfLab/tree/master/ws-impls/ws-java-servlet-blocking">ws-java-servlet-blocking</a>
- <a href="WSPerfLab/tree/master/ws-impls/ws-java-servlet-nonblocking">ws-java-servlet-nonblocking</a>
- <a href="WSPerfLab/tree/master/ws-impls/ws-java-vertx">ws-java-vertx</a>
- <a href="WSPerfLab/tree/master/ws-impls/ws-java-netty">ws-java-netty</a>
- <a href="WSPerfLab/tree/master/ws-impls/ws-clojure-noir">ws-clojure-noir</a>
- <a href="WSPerfLab/tree/master/ws-impls/ws-clojure-vertx">ws-clojure-vertx</a>
- <a href="WSPerfLab/tree/master/ws-impls/ws-nodejs">ws-nodejs</a>
- <a href="WSPerfLab/tree/master/ws-impls/ws-python-gevent">ws-python-gevent</a>


The <a href="WSPerfLab/tree/master/ws-client">ws-client</a> will drive the traffic and capture performance metrics.

Metrics to be captured are:

- client-side end-to-end latency for entire trip including network
- response payload size

# Test Implementations

Information about test cases and implementation requirements can be found in the <a href="WSPerfLab/tree/master/ws-impls">ws-impls README</a>.


# Statistics and Report

