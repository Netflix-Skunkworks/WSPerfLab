package perf.test.netty.client;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.PropertyNames;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

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
public class NettyClient {

    private Logger logger = LoggerFactory.getLogger(NettyClient.class);

    private Channel channel;
    private final ClientStateChangeListener stateChangeListener;
    private ClientCompletionListener currentRequestCompletionListener;
    private final String host;
    private final int port;
    private final ClientBootstrap bootstrap;
    private AtomicBoolean inUse = new AtomicBoolean();
    private long lastUsedTime = System.currentTimeMillis();

    NettyClient(ClientBootstrap bootstrap, ClientStateChangeListener stateChangeListener, String host, int port) {
        this.bootstrap = bootstrap;
        this.stateChangeListener = stateChangeListener;
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
    public void get(URI uri, ClientCompletionListener listener) {
        validateIfInUse(); // Throws an exception if in use.
        currentRequestCompletionListener = listener;
        channel.setAttachment(this);
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getSchemeSpecificPart());
        request.setHeader(HttpHeaders.Names.HOST, host);
        channel.write(request);
    }

    boolean isExpired(long now) {
        long timeInWaiting = now - lastUsedTime;
        return (timeInWaiting + 10) > PropertyNames.ClientIdleTimeOutMs.getValueAsInt();
    }

    private void validateIfInUse() {
        if (!inUse.compareAndSet(false, true)) {
            throw new IllegalStateException("Client in use or is closing.");
        }
    }

    void release() {
        currentRequestCompletionListener = null;
        inUse.set(false);
        if (null != stateChangeListener) {
            stateChangeListener.onClose(this);
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
        if (null != channel) {
            channel.getCloseFuture().awaitUninterruptibly();
        }
    }

    void closeAndForget() {
        if (null != channel) {
            channel.disconnect();
        }
    }

    boolean isConnected() {
        return (null != channel && channel.isConnected());
    }

    ClientCompletionListener getCurrentRequestCompletionListener() {
        return currentRequestCompletionListener;
    }

    interface ClientStateChangeListener {

        void onClose(NettyClient client);
    }

    public interface ClientCompletionListener {

        void onComplete(HttpResponse response);

        void onError(ExceptionEvent exceptionEvent);
    }
}
