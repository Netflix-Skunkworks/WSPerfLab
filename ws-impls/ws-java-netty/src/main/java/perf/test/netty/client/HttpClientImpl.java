package perf.test.netty.client;

import javax.annotation.Nullable;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.PropertyNames;
import perf.test.netty.server.StatusRetriever;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Nitesh Kant
 */
public class HttpClientImpl implements HttpClient<FullHttpResponse, FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientImpl.class);

    private final DedicatedClientPool<FullHttpResponse, FullHttpRequest> pool;
    @Nullable private final EventExecutor executor;

    private final AtomicLong inflighRequests = new AtomicLong();
    private final AtomicLong recvRequests = new AtomicLong();

    private static final ConcurrentLinkedQueue<RequestProcessingPromise> allPromises = new ConcurrentLinkedQueue<RequestProcessingPromise>();

    HttpClientImpl(@Nullable EventExecutor executor, DedicatedClientPool<FullHttpResponse, FullHttpRequest> pool) {
        this.executor = executor;
        this.pool = pool;
    }

    @Override
    public Future<FullHttpResponse> execute(@Nullable EventExecutor executor, final FullHttpRequest request) {
        inflighRequests.incrementAndGet();
        recvRequests.incrementAndGet();
        final EventExecutor _executor;
        if (null == executor) {
            _executor = this.executor;
        } else {
            _executor = executor;
        }

        if (null == _executor) {
            logger.error("No event executor configured or passed in which the callbacks can be made.");
            throw new IllegalArgumentException("No event executor configured or passed in which the callbacks can be made.");
        }


        final Future<DedicatedHttpClient<FullHttpResponse,FullHttpRequest>> clientGetFuture = pool.getClient(_executor);

        final RequestProcessingPromise processingFinishPromise = new RequestProcessingPromise(_executor, clientGetFuture);
        if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
            allPromises.add(processingFinishPromise);
        }
        processingFinishPromise.addListener(new GenericFutureListener<Future<? super FullHttpResponse>>() {
            @Override
            public void operationComplete(Future<? super FullHttpResponse> future) throws Exception {
                inflighRequests.decrementAndGet();
                if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
                    allPromises.remove(processingFinishPromise);
                }
            }
        });
        clientGetFuture.addListener(new ConnectFutureListener(request, processingFinishPromise));

        return processingFinishPromise;
    }

    @Override
    public void populateStatus(StatusRetriever.TestCaseStatus testCaseStatus) {
        pool.populateStatus(testCaseStatus);
        testCaseStatus.setHttpClientInflightRequests(inflighRequests.get());
        testCaseStatus.setHttpClientReqRecvCount(recvRequests.get());
    }

    @Override
    public void populateTrace(StringBuilder traceBuilder) {
        if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
            for (RequestProcessingPromise aPromise : allPromises) {
                aPromise.populateTrace(traceBuilder);
            }
        }
    }

    public static class RequestProcessingPromise extends DefaultPromise<FullHttpResponse> implements RequestExecutionPromise<FullHttpResponse> {

        @Nullable
        private EventExecutor _executor;
        private final Future<DedicatedHttpClient<FullHttpResponse, FullHttpRequest>> clientGetFuture;
        private Future<FullHttpResponse> clientProcessingFuture;
        private final ConcurrentLinkedQueue<String> checkpoints = new ConcurrentLinkedQueue<String>();

        public RequestProcessingPromise(@Nullable EventExecutor _executor,
                                        Future<DedicatedHttpClient<FullHttpResponse,FullHttpRequest>> clientGetFuture) {
            this._executor = _executor;
            this.clientGetFuture = clientGetFuture;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (clientGetFuture.isCancellable()) {
                clientGetFuture.cancel(mayInterruptIfRunning);
            } else if (null != clientProcessingFuture && clientProcessingFuture.isCancellable()) {
                clientProcessingFuture.cancel(mayInterruptIfRunning);
            }

            return super.cancel(mayInterruptIfRunning);
        }

        public void checkpoint(String checkpoint) {
            checkpoints.add(checkpoint);
        }

        void setClientProcessingFuture(final RequestExecutionPromise<FullHttpResponse> clientProcessingFuture) {
            this.clientProcessingFuture = clientProcessingFuture;
            this.clientProcessingFuture.addListener(new GenericFutureListener<Future<FullHttpResponse>>() {
                @Override
                public void operationComplete(Future<FullHttpResponse> future) throws Exception {
                    _executor = clientProcessingFuture.getExecutingClientExecutor();
                    if (future.isSuccess()) {
                        setSuccess(future.get());
                    } else {
                        setFailure(future.cause());
                    }
                }
            });
        }

        public void populateTrace(StringBuilder traceBuilder) {
            traceBuilder.append('\n');
            traceBuilder.append("****************************************");
            traceBuilder.append('\n');
            for (String checkpoint : checkpoints) {
                traceBuilder.append("->");
                traceBuilder.append(checkpoint);
            }
            traceBuilder.append('\n');
            traceBuilder.append("****************************************");
        }

        @Override
        protected EventExecutor executor() {
            return _executor;
        }

        @Override
        public EventExecutor getExecutingClientExecutor() {
            if (null == _executor) {
                throw new IllegalArgumentException("Executor is available after promise is done.");
            }
            return _executor;
        }
    }

    private static class ConnectFutureListener
            implements GenericFutureListener<Future<DedicatedHttpClient<FullHttpResponse, FullHttpRequest>>> {
        private final FullHttpRequest request;
        private final RequestProcessingPromise processingFinishPromise;

        public ConnectFutureListener(FullHttpRequest request, RequestProcessingPromise processingFinishPromise) {
            this.request = request;
            this.processingFinishPromise = processingFinishPromise;
        }

        @Override
        public void operationComplete(
                Future<DedicatedHttpClient<FullHttpResponse, FullHttpRequest>> future)
                throws Exception {
            if (future.isSuccess()) {
                processingFinishPromise.checkpoints.add("Connect success");
                DedicatedHttpClient<FullHttpResponse, FullHttpRequest> dedicatedClient = future.get();
                processingFinishPromise.checkpoints.add("Going to enqueue request.");
                RequestExecutionPromise<FullHttpResponse> clientProcessingPromise = dedicatedClient.execute(request, processingFinishPromise);
                processingFinishPromise.checkpoints.add("Request enqueued");
                processingFinishPromise.setClientProcessingFuture(clientProcessingPromise);
            } else {
                processingFinishPromise.checkpoints.add("Connect failed.");
                processingFinishPromise.setFailure(future.cause());
            }
        }
    }
}
