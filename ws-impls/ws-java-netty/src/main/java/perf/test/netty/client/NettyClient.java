package perf.test.netty.client;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class NettyClient {

    private Logger logger = LoggerFactory.getLogger(NettyClient.class);

    private Channel channel;
    private final ClientCloseListener closeListener;
    private final String host;
    private final int port;
    private final ClientBootstrap bootstrap;
    private AtomicBoolean inUse = new AtomicBoolean();

    NettyClient(ClientBootstrap bootstrap, ClientCloseListener closeListener, String host, int port) {
        this.bootstrap = bootstrap;
        this.closeListener = closeListener;
        this.host = host;
        this.port = port;
    }

    public Future<String> get(URI uri) {
        validateIfInUse(); // Throws an exception if in use.
        ClientHandler handler = (ClientHandler) channel.getPipeline().get("handler");
        handler.reset();
        channel.setAttachment(this);
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getSchemeSpecificPart());
        request.setHeader(HttpHeaders.Names.HOST, host);
        return new RequestFuture(channel.write(request));
    }

    private void validateIfInUse() {
        if (!inUse.compareAndSet(false, true)) {
            throw new IllegalStateException("Client in use or is closing.");
        }
    }

    void release() {
        inUse.set(false);
        if (null != closeListener) {
            closeListener.onClose(this);
        }

    }

    ChannelFuture connect() throws Throwable {
        if (isConnected()) {
            return null;
        }
        bootstrap.setOption("child.keepAlive", true);
        ChannelFuture connectFuture = bootstrap.connect(new InetSocketAddress(host, port));
        connectFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    channel = future.getChannel();
                } else {
                    logger.error(String.format("Connect failed for host: %s and port: %s", host, port), future.getCause());
                }
            }
        });
        return connectFuture;
    }

    void dispose() {
        channel.getCloseFuture().awaitUninterruptibly();
    }

    public boolean isConnected() {
        return (null != channel && channel.isConnected());
    }

    interface ClientCloseListener {

        void onClose(NettyClient closedClient);
    }

    private class RequestFuture implements Future<String> {

        private final ChannelFuture writeFuture;
        private final ClientHandler handler;

        public RequestFuture(ChannelFuture writeFuture) {
            this.writeFuture = writeFuture;
            handler = (ClientHandler) channel.getPipeline().get("handler");
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return writeFuture.cancel();
        }

        @Override
        public boolean isCancelled() {
            return writeFuture.isCancelled();
        }

        @Override
        public boolean isDone() {
            return handler.isDone();
        }

        @Override
        public String get() throws InterruptedException, ExecutionException {
            while (!handler.hasContent()) {
                synchronized (handler.contentPopulationLock) {
                    handler.contentPopulationLock.wait();
                }
            }
            return handler.getContent();
        }

        @Override
        public String get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            long startTime = System.currentTimeMillis();
            long waitTime = unit.toMillis(timeout);
            while (!handler.isDone()) {
                synchronized (handler.contentPopulationLock) {
                    if (waitTime <= 0) {
                        handler.contentPopulationLock.wait();
                        continue;
                    } else {
                        handler.contentPopulationLock.wait(waitTime);
                    }
                    waitTime = waitTime - (System.currentTimeMillis() - startTime);
                    if (waitTime < 0) {
                        break;
                    }
                }
            }

            if (handler.isDone()) {
                if (handler.isSuccess()) {
                    return handler.getContent();
                } else {
                    throw new ExecutionException(
                            "Client request failed with status code: " + handler.getResponseStatusCode(), null);
                }
            } else {
                throw new TimeoutException(String.format("No result after waiting for %s milliseconds.", timeout));
            }
        }
    }
}
