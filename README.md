# WSPerfLab
=========

Project for testing web-service implementations.

The test setup will consist of the following:

<img src="https://raw.github.com/wiki/benjchristensen/WSPerfLab/images/overview.png" width="860" height="300">

The <a href="WSPerfLab/ws-backend-simulation">ws-backend-simulation</a> is a simple Java backend app accepting request arguments to affect its response size and latency.

The various test implementations are intended to each implement the same logic with different architectures, languages and frameworks.

- <a href="WSPerfLab/ws-java-servlet-blocking">ws-java-servlet-blocking</a>
- <a href="WSPerfLab/ws-java-servlet-nonblocking">ws-java-servlet-nonblocking</a>
- <a href="WSPerfLab/ws-java-vertx">ws-java-vertx</a>
- <a href="WSPerfLab/ws-java-netty">ws-java-netty</a>
- <a href="WSPerfLab/ws-clojure-noir">ws-clojure-noir</a>
- <a href="WSPerfLab/ws-clojure-vertx">ws-clojure-vertx</a>
- <a href="WSPerfLab/ws-nodejs">ws-nodejs</a>
- <a href="WSPerfLab/ws-python-gevent">ws-python-gevent</a>


The <a href="WSPerftLab/ws-client">ws-client</a> will drive the traffic and capture performance metrics.

Metrics to be captured are:

- server-side end-to-end latency as returned in response headers
- client-side end-to-end latency for entire trip including network
- response payload size


# Request Use Cases


# Statistics and Report

