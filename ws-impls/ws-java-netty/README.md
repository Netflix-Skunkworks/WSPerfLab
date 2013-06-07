# Netty Non-blocking impl

This module contains a non-blocking implementation of ws test impl using <a href="https://netty.io/">netty</a>.
The module provides both client and server HTTP implementations to be used for performace benchmarks.

# Server

The netty server uses a start class called perf.test.netty.server.ServerBootstrap to startup the standalone server.
On startup, the server does the following:
* Opens the server port (defaults to 8888 and overriden by system property http.server.port) for accepting HTTP requests.
* Initializes a netty client pool for sending requests to the mock backend of this perf infrastructure.
* Starts receiving any requests on the server port with base context path "/ws-java-netty/" and conforming to the
standards of this benchmark.


## Server configuration

This server can be configured using the following optional system properties:

* http.server.port: Specify the port at which this server must run.
* server.log.enable: Enable verbose logging (netty) for the server
* server.chunk.size: Chunk size for the netty server pipeline. This size is used to instanruare the
<a href="http://static.netty.io/3.6/api/org/jboss/netty/handler/codec/http/HttpChunkAggregator.html#HttpChunkAggregator(int)">Netty Http Chunk aggregator</a>
configured in the server pipeline.
* http.server.context.path: The context path for the server. All testcase requests will be served from this context path.
The default value is: "/ws-java-netty/". If overridden, the new values must contain the starting and trailing slashes.

## Assumptions

* All HTTP traffic send to this server is HTTP protocol 1.1 i.e. uses keep alive connections.

# Client

A netty based non-blocking client for outbound requests to the mock backend.

## Connection Pool

This client uses a connection pool for all outbound backend requests. All the test cases uses a different connection
pool for the purpose of isolation.

If the connection pool is exhausted and no new connections can be obtained, the server fails fast and sends a HTTP
response of 500 (This should be 503 but for the reason of simplicity, its not implemented. It can be implemented if required)

### Exhaustion

Connection pool can exhaust for two reasons:

* Genuine load.
* Bust of disconnections from the backend.

In the second case above, the pool will try to get any available & connected backend connections before throwing an error.
In case, no connected connections are available (out of the max connection limit), the pool is deemed exhausted.
However, as soon as a connection is identified as closed, the system tries to re-establish the connection asynchronously.

For details about the client implementation, see the javadocs for perf.test.netty.client.NettyClientPool


## Client configuration

The client can be configured using the following optional system properties:

* client.log.enable: Enable verbose logging (netty) for the client.
* client.chunk.size: Chunk size for the netty server pipeline. This size is used to instanruare the
 <a href="http://static.netty.io/3.6/api/org/jboss/netty/handler/codec/http/HttpChunkAggregator.html#HttpChunkAggregator(int)">Netty Http Chunk aggregator</a>
  configured in the client pipeline.
* perf.test.backend.host.maxconn.per.test: Number of connections to the mock backend. This is an aggressive pool size and
is connected at server startup. Default: 100


# Mock backend

This module uses the mock backend provided by the infrastructure. The endpoint configurations are specified as the
following system properties:

* perf.test.backend.host: The hostname for the mock backend machine. Default: localhost
* perf.test.backend.port: The port for the mock backend server. Default: 8989
* perf.test.backend.context.path: The base context path for all requests to this server. Default: /ws-backend-mock


# Netty threads

All the netty thread pools (boss and worker) are created as java cached executors.
