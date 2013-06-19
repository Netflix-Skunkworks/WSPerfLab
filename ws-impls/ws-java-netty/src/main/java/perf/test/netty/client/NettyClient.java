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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A client abstraction for non-blocking clients. <br/>
 * <b> This client only allows one request processing at any time.</b> <br/>
 *
 * The client is deemed in use till the time, the current request is completed, specifically till the time,
 * {@link ClientHandler#messageReceived(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.MessageEvent)}
 * is not called. As soon as this method is invoked (which marks the completion of response reading), the client is
 * released for further use. Any user of this client is free to use it for as many sequential request processing as
 * required. <p/>
 * Since, every request is processed using a new pipeline (created by {@link ClientPipelineFactory} and hence a new
 * {@link ClientHandler}, the state of the request processing, is stored in the handler. <br/>
 * The future associated with a request processing, stores a reference to the handler and hence can get the state of
 * the request processing anytime. <br/>
 * {@link ClientHandler} releases the client for reuse so the user of this client, need not do any explicity client
 * release.
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
class NettyClient {

    private Logger logger = LoggerFactory.getLogger(NettyClient.class);

    private Channel channel;
    private final ChannelFutureListener channelCloseListener;
    private NettyClientPool.ClientCompletionListener currentRequestCompletionListener;
    private final String host;
    private final int port;
    private final ClientBootstrap bootstrap;
    private final ReentrantLock owningLock = new ReentrantLock();

    NettyClient(ClientBootstrap bootstrap, ChannelFutureListener channelCloseListener, String host,
                int port) {
        this.bootstrap = bootstrap;
        this.channelCloseListener = channelCloseListener;
        this.host = host;
        this.port = port;
    }

    /**
     * Executes an HTTP get request for the passed uri.
     * <b>The host, port & scheme information in the passed URI, if any, is ignored. This information is taken from the
     * associated {@link NettyClientPool}</b>
     * The passed handler is invoked (in the selector thread) after a response is received.
     *
     * @param uri URI for the request.
     *
     * @return The future to retrieve the result.
     */
    void get(URI uri, NettyClientPool.ClientCompletionListener listener) {
        if (!owningLock.isHeldByCurrentThread()) {
            throw new IllegalStateException("This thread: " + Thread.currentThread().getName() +
                                            ". Client instance not owned by current thread. Is owned by anyone? " + owningLock.isLocked() + ". Lock state: " + owningLock);
        }
        currentRequestCompletionListener = listener;
        channel.setAttachment(this);
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getSchemeSpecificPart());
        request.setHeader(HttpHeaders.Names.HOST, host);
        channel.write(request);
    }

    ChannelFuture connect() {
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
                    if (null != channelCloseListener) {
                        channel.getCloseFuture().addListener(channelCloseListener);
                    }
                } else {
                    logger.error(String.format("Connect failed for host: %s and port: %s", host, port), future.getCause());
                }
            }
        });
        return connectFuture;
    }

    void dispose() {
        if (null != channel) {
            channel.getCloseFuture().awaitUninterruptibly();
        }
    }

    boolean isConnected() {
        return (null != channel && channel.isConnected());
    }

    void release() {
        if (!owningLock.isHeldByCurrentThread()) {
            logger.error("Attempt to release the client, when it was not owned.");
        } else {
            owningLock.unlock();
        }

        currentRequestCompletionListener = null;
    }

    boolean claim() {
        if (!owningLock.isHeldByCurrentThread()) {
            if (!owningLock.tryLock()) {
                 return false;
            }
        }

        if (!isConnected()) {
            owningLock.unlock(); // Will not use it.
            return false;
        } else {
            return true;
        }
    }

    NettyClientPool.ClientCompletionListener getCurrentRequestCompletionListener() {
        return currentRequestCompletionListener;
    }
}
