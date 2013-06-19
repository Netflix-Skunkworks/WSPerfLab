package perf.test.netty.client;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.PropertyNames;
import perf.test.netty.server.StatusRetriever;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pool of {@link NettyClient}s. This is an aggressively initialized client pool, the max connections of which are
 * initialized & connected on startup. The initialization of this pool awaits till all the connection attempts have
 * completed either with an error or success.<br/>
 * A {@link ClientBootstrap} instance is shared across all clients in this pool. <br/>
 * After startup, all new client connections are done in async mode and no method calls to this pool blocks.
 * The pool fails fast on exhaustion of connections since all connect attempts are async. <br/>
 * This pool attempts to bootstrap the connections (upto the max connections) whenever it finds on a connection attempt
 * that there are no valid connections in the pool. This will happen when the backend was down on the last connection
 * attempt. So, in most cases, this pool will be resilient to backend servers which come and go frequently.
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class NettyClientPool {

    private Logger logger = LoggerFactory.getLogger(NettyClientPool.class);

    private LinkedBlockingQueue<NettyClient> clients;
    private LinkedBlockingQueue<Request> requestQueue;
    private final String host;
    private int maxConnections;
    private final int port;
    private ClientBootstrap bootstrap;
    private volatile boolean shutdown;
    private final AtomicInteger connCount = new AtomicInteger();
    private final AtomicInteger unhandledRequests = new AtomicInteger();

    private final ExecutorService requestQueueExecutor;

    public NettyClientPool(int maxConnections, int connectionAtBootstrap, final int port, final String host,
                           int requestQueueSize) throws InterruptedException {
        this.maxConnections = maxConnections;
        this.port = port;
        this.host = host;
        bootstrap();
        clients = new LinkedBlockingQueue<NettyClient>(maxConnections);
        requestQueue = new LinkedBlockingQueue<Request>(requestQueueSize);
        for (int i = 0; i < connectionAtBootstrap; i++) {
            connectNewClientAsync(true);
        }
        requestQueueExecutor = Executors.newCachedThreadPool(); // Fixed number of threads are always running

        for (int i = 0; i < PropertyNames.ClientBacklogCleanerThreadCount.getValueAsInt(); i++) {
            requestQueueExecutor.execute(new Runnable() {

                private final Object continueMonitor = new Object();

                @Override
                public void run() {
                    NettyClient clientToUse = null;
                    while (true) { // Blocks till client/request is available.
                        try {
                            synchronized (continueMonitor) {
                                continueMonitor.wait(); // will be notified when the current get request finishes.
                            }
                            Request toProcess = requestQueue.take();
                            if (null == clientToUse) {
                                clientToUse = newClient("backlog.cleaner"); // So we essentially have one client per cleaner.
                                connCount.incrementAndGet();
                            }

                            if (!clientToUse.claim()) {
                                clientToUse.connect().awaitUninterruptibly(); // This may result in timeout of the request to be processed, but in warmed up state, this will work fine.
                            }

                            toProcess.completionListener = new ClientCompletionListenerWrapper(toProcess.completionListener.delegate, toProcess.uri) {
                                @Override
                                protected void _releaseClient(NettyClient clientToUse) {
                                    synchronized (continueMonitor) {
                                        continueMonitor.notify();
                                    }
                                }
                            };
                            makeGetRequestOnClient(toProcess, clientToUse, true);
                        } catch (InterruptedException e) {
                            logger.info("Netty client backlog cleaner interrupted, shutting down now.");
                            break;
                        }
                    }
                }
            });
        }
    }

    public void shutdown() {
        logger.info("Shutting down client pool.");
        shutdown = true;
        for (NettyClient nettyClient : clients) {
            nettyClient.dispose();
        }
        bootstrap.releaseExternalResources();
    }

    public void sendGetRequest(URI uri, ClientCompletionListener completionListener) {
        if (shutdown) {
            logger.warn("Netty client pool is shutting down.");
            throw new RejectedExecutionException("Netty client pool is shutting down.");
        }
        final Request newRequest = new Request(uri, completionListener);
        if (requestQueue.offer(newRequest)) {
            NettyClient clientToUse = null;
            try {
                clientToUse = getNextAvailableClient();
                if (null == clientToUse) {
                    // Time to increase the pool if required.
                    int connectionCount = connCount.getAndIncrement();
                    if (connectionCount < maxConnections) {
                        logger.info("Creating a new connection, connection count till now: " + connectionCount);
                        connectNewClientAsync(false);
                    } else {
                        connCount.decrementAndGet();
                        logger.info("No more connections can be created. Connection count till now: " + connectionCount);
                    }
                } else {
                    Request requestToExecute = requestQueue.poll();
                    if (null != requestToExecute) {
                        makeGetRequestOnClient(requestToExecute, clientToUse, true);
                    } else {
                        releaseClient(clientToUse); // didn't use it so release.
                    }
                }
            } catch (InterruptedException e) {
                // Should not happen as we do not block.
                Thread.interrupted();
                logger.warn("Send get request interrupted, strange as we do not block.", e);
            }
        } else {
            logger.error("Backend request backlog very high, rejecting requests.");
            throw new RejectedExecutionException("Backend request backlog very high, rejecting requests.");
        }

    }

    void retry(ClientCompletionListenerWrapper completionListenerWrapper) {
        sendGetRequest(completionListenerWrapper.requestUri, completionListenerWrapper.delegate);
    }

    void onUnhandledRequest(NettyClient client) {
        unhandledRequests.incrementAndGet();
    }

    private NettyClient getNextAvailableClient() throws InterruptedException {
        if (shutdown) {
            logger.warn("Netty client pool is shutting down.");
            throw new RejectedExecutionException("Netty client pool is shutting down.");
        }
        final NettyClient client = clients.poll();
        if (null != client) {
            if (!client.claim()) {
                return getNextAvailableClient();
            }
        }
        return client;
    }

    private void releaseClient(NettyClient client) {
        client.release();
        if (clients.offer(client)) {
            logger.debug("Connection returned to pool.");
        } else {
            logger.info("Could not return connection back to pool.");
        }
    }

    private void connectNewClientAsync(boolean incrementConnCounter) {
        final NettyClient clientToUse = newClient("pool");
        if (clients.offer(clientToUse)) {
            if (incrementConnCounter) {
                connCount.incrementAndGet();
            }
            clientToUse.connect().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        logger.info(String.format("A client connected to host %s at port %s", host, port));
                        Request request = requestQueue.poll();
                        if (null != request && clientToUse.claim()) { // since its connected, someone else may have claimed it.
                            // We do not process queued messages from the connection callback as that can be costly for the
                            // boss thread which is generally == 1. So, we do not set the client here before firing the
                            // request.
                            makeGetRequestOnClient(request, clientToUse, false);
                        }
                    } else {
                        logger.info("Client connection failed.", future.getCause());
                        connCount.decrementAndGet();// Since we increment the count before connecting, we decrement here.
                    }
                }
            });
        } else {
            // Connections limit reached.
            logger.warn("Failed to enqueue the new client connections, connection limit is reached. This can still process the requests albit slowly.");
            connCount.decrementAndGet();// Since we increment the count before connecting, we decrement here.
        }
    }

    private void makeGetRequestOnClient(Request request, NettyClient clientToUse,
                                        boolean processMoreMessagesOnCompletion) {
        if (processMoreMessagesOnCompletion) {
            request.completionListener.clientToUse = clientToUse;
        }
        clientToUse.get(request.uri, request.completionListener);
    }

    private NettyClient newClient(String owner) {
        return new NettyClient(bootstrap, new ChannelFutureListener() {

            private AtomicBoolean countDecreased = new AtomicBoolean();

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (countDecreased.compareAndSet(false, true)) {
                    logger.info("Client channel closed.");
                    connCount.decrementAndGet();
                }
            }
        }, host, port);
    }

    private void bootstrap() {
        bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
        bootstrap.setPipelineFactory(new ClientPipelineFactory(this) /*This is bad as we are leaking "this" before construction*/);
    }

    public void populateStatus(StatusRetriever.TestCaseStatus testCaseStatus) {
        testCaseStatus.setConnectionsCount(connCount.get());
        testCaseStatus.setRequestQueueSize(requestQueue.size());
        testCaseStatus.setUnhandledRequestsSinceStartUp(unhandledRequests.get());
    }

    public interface ClientCompletionListener {

        void onComplete(HttpResponse response);

        void onError(ExceptionEvent exceptionEvent);
    }

    class ClientCompletionListenerWrapper implements ClientCompletionListener {

        private ClientCompletionListener delegate;
        private URI requestUri;
        private NettyClient clientToUse;

        ClientCompletionListenerWrapper(ClientCompletionListener delegate, URI requestUri) {
            this.delegate = delegate;
            this.requestUri = requestUri;
        }

        @Override
        public void onComplete(HttpResponse response) {
            delegate.onComplete(response);
            processQueuedMessages(clientToUse);
        }

        @Override
        public void onError(ExceptionEvent exceptionEvent) {
            delegate.onError(exceptionEvent);
            processQueuedMessages(clientToUse);
        }

        protected void processQueuedMessages(NettyClient clientToUse) {
            if (null == clientToUse) {
                return;
            }
            Request requestToProcess = requestQueue.poll();
            if (null != requestToProcess) {
                makeGetRequestOnClient(requestToProcess, clientToUse, true);
            } else {
                _releaseClient(clientToUse);
            }
        }

        protected void _releaseClient(NettyClient clientToUse) {
            releaseClient(clientToUse);
        }
    }

    private class Request {

        private URI uri;
        private ClientCompletionListenerWrapper completionListener;

        public Request(URI uri, ClientCompletionListener completionListener) {
            this.uri = uri;
            this.completionListener = new ClientCompletionListenerWrapper(completionListener, uri);
        }
    }
}
