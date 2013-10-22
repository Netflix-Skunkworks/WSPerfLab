package perf.test.netty.client;

import io.netty.handler.codec.http.HttpRequest;

import java.net.InetSocketAddress;

/**
 * @author Nitesh Kant
 */
public interface LoadBalancer<R extends HttpRequest> {

    InetSocketAddress nextServer(R request); /// TODO: Not the best of the things to return.

    InetSocketAddress[] getAllServers();
}
