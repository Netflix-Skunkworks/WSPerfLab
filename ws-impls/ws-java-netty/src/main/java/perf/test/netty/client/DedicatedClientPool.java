package perf.test.netty.client;

import com.google.common.base.Preconditions;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.server.StatusRetriever;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 *
 * @author Nitesh Kant
 */
class DedicatedClientPool<T, R extends HttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(DedicatedClientPool.class);

    public static final String RESPONSE_HANDLER_ATTR_KEY_NAME = "response_handler";
    public static final String PROCESSING_COMPLETE_PROMISE_KEY_NAME = "processing_complete_promise";
    public static final AttributeKey<AtomicInteger> RETRY_COUNT_KEY = new AttributeKey<AtomicInteger>("retry_count");

    private final AttributeKey<HttpClient.ClientResponseHandler<T>> responseHandlerKey =
            new AttributeKey<HttpClient.ClientResponseHandler<T>>(RESPONSE_HANDLER_ATTR_KEY_NAME);
    private final AttributeKey<Promise<T>> processingCompletePromiseKey =
            new AttributeKey<Promise<T>>(PROCESSING_COMPLETE_PROMISE_KEY_NAME);

    protected final Bootstrap bootstrap;
    protected final InetSocketAddress serverAddress;

    private final AtomicInteger unhandledRequests = new AtomicInteger();

    private final int maxConnections;

    private final LinkedBlockingQueue<Object> clientLimitEnforcer;
    private final ConcurrentLinkedQueue<DedicatedHttpClient<T,R>> availableClients; // unbounded as the # of conn is bounded by allClients queue.
    private final int coreConnections;

    DedicatedClientPool(InetSocketAddress serverAddress, Bootstrap bootstrap, int maxConnections, int coreConnections) {
        this.coreConnections = coreConnections;
        Preconditions.checkArgument(coreConnections <= maxConnections,
                                    "Core connection count can not be more than max connections.");
        this.maxConnections = maxConnections;
        this.bootstrap = bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        this.serverAddress = serverAddress;
        clientLimitEnforcer = new LinkedBlockingQueue<Object>(this.maxConnections);
        availableClients = new ConcurrentLinkedQueue<DedicatedHttpClient<T, R>>();
    }

    void init() throws PoolExhaustedException {
        for (int i = 0; i < coreConnections; i++) {
            createNewClientEagerly();
        }
    }

    private void createNewClientEagerly() {
        createNewClientOnDemand(null);
    }

    private void createNewClientOnDemand(@Nullable final Promise<DedicatedHttpClient<T, R>> completionPromise) {
        final Object clientLimitEnforcingToken = new Object();
        if (clientLimitEnforcer.offer(clientLimitEnforcingToken)) {
            bootstrap.connect(serverAddress).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        logger.info("New client connected for host {} and port {}", serverAddress.getHostName(),
                                    serverAddress.getPort());
                        final DedicatedHttpClient<T, R> httpClient = getHttpClient(future.channel());
                        future.channel().closeFuture().addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                logger.info("Client disconnected from host {} and port {}", serverAddress.getHostName(),
                                            serverAddress.getPort());
                                clientLimitEnforcer.remove(clientLimitEnforcingToken);
                                availableClients.remove(httpClient);
                            }
                        });
                        if (null == completionPromise) {
                            addAvailableClient(httpClient);
                        } else {
                            completionPromise.setSuccess(httpClient);
                        }
                    } else {
                        clientLimitEnforcer.remove(clientLimitEnforcingToken);
                        logger.error(String.format("Failed to connect to host %s and port %d",
                                                   serverAddress.getHostName(),
                                                   serverAddress.getPort()), future.cause());
                        if (null != completionPromise) {
                            completionPromise.setFailure(future.cause());
                        }
                    }
                }
            });
        } else {
            if (null == completionPromise) {
                logger.error(
                        "Eager connection attempt failed. Pool exhausted, can not create any more connection to host {} and port {}.",
                        serverAddress.getHostName(),
                        serverAddress.getPort());
            } else {
                logger.error(
                        "On demand connection attempt failed. Pool exhausted, can not create any more connection to host {} and port {}.",
                        serverAddress.getHostName(),
                        serverAddress.getPort());
                completionPromise.setFailure(new PoolExhaustedException(serverAddress, maxConnections));
            }
        }
    }

    private void addAvailableClient(DedicatedHttpClient<T, R> httpClient) {
        availableClients.add(httpClient);
    }

    Future<DedicatedHttpClient<T, R>> getClient(EventExecutor executor) {

        @Nullable DedicatedHttpClient<T, R> availableClient = availableClients.poll();
        final Promise<DedicatedHttpClient<T, R>> clientCreationPromise = new DefaultPromise<DedicatedHttpClient<T, R>>(executor);
        if (null == availableClient) {
            createNewClientOnDemand(clientCreationPromise);
        } else {
            clientCreationPromise.setSuccess(availableClient);
        }
        return clientCreationPromise;
    }

    AttributeKey<HttpClient.ClientResponseHandler<T>> getResponseHandlerKey() {
        return responseHandlerKey;
    }

    AttributeKey<Promise<T>> getProcessingCompletePromiseKey() {
        return processingCompletePromiseKey;
    }

    void returnClient(DedicatedHttpClient<T, R> clientToReturn) {
        if (clientToReturn.isActive()) {
            availableClients.add(clientToReturn);
        } else {
            logger.info("Inactive client returned, not adding back to the pool.");
        }
    }

    public void populateStatus(StatusRetriever.TestCaseStatus testCaseStatus) {
        testCaseStatus.setAvailConnectionsCount(availableClients.size());
        testCaseStatus.setTotalConnectionsCount(clientLimitEnforcer.size());
        testCaseStatus.setUnhandledRequestsSinceStartUp(unhandledRequests.get());
    }

    void onUnhandledRequest() {
        unhandledRequests.incrementAndGet();
    }

    protected DedicatedHttpClient<T, R> getHttpClient(Channel channel) {
        return new DedicatedHttpClient<T, R>(channel, serverAddress.getHostName(), this);
    }

    public void shutdown() {
    }
}
