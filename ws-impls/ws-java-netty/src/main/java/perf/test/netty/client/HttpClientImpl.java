package perf.test.netty.client;

import com.sun.istack.internal.Nullable;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Nitesh Kant
 */
public class HttpClientImpl implements HttpClient<FullHttpResponse, FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientImpl.class);

    private final DedicatedClientPool<FullHttpResponse, FullHttpRequest> pool;
    @Nullable private final EventExecutor executor;

    HttpClientImpl(@Nullable EventExecutor executor, DedicatedClientPool<FullHttpResponse, FullHttpRequest> pool) {
        this.executor = executor;
        this.pool = pool;
    }

    @Override
    public Future<FullHttpResponse> execute(@Nullable EventExecutor executor, final FullHttpRequest request,
                                            final ClientResponseHandler<FullHttpResponse> responseHandler) {
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

        clientGetFuture.addListener(new ConnectFutureListener(request, responseHandler, processingFinishPromise));

        return processingFinishPromise;
    }

    private static class RequestProcessingPromise extends DefaultPromise<FullHttpResponse> {

        private final Future<DedicatedHttpClient<FullHttpResponse, FullHttpRequest>> clientGetFuture;
        private Future<FullHttpResponse> clientProcessingFuture;

        public RequestProcessingPromise(EventExecutor _executor,
                                        Future<DedicatedHttpClient<FullHttpResponse,FullHttpRequest>> clientGetFuture) {
            super(_executor);
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

        void setClientProcessingFuture(Future<FullHttpResponse> clientProcessingFuture) {
            this.clientProcessingFuture = clientProcessingFuture;
            this.clientProcessingFuture.addListener(new GenericFutureListener<Future<FullHttpResponse>>() {
                @Override
                public void operationComplete(Future<FullHttpResponse> future) throws Exception {
                    if (future.isSuccess()) {
                        setSuccess(future.get());
                    } else {
                        setFailure(future.cause());
                    }
                }
            });
        }
    }

    private static class ConnectFutureListener
            implements GenericFutureListener<Future<DedicatedHttpClient<FullHttpResponse, FullHttpRequest>>> {
        private final FullHttpRequest request;
        private final ClientResponseHandler<FullHttpResponse> responseHandler;
        private final RequestProcessingPromise processingFinishPromise;

        public ConnectFutureListener(FullHttpRequest request, ClientResponseHandler<FullHttpResponse> responseHandler,
                                     RequestProcessingPromise processingFinishPromise) {
            this.request = request;
            this.responseHandler = responseHandler;
            this.processingFinishPromise = processingFinishPromise;
        }

        @Override
        public void operationComplete(
                Future<DedicatedHttpClient<FullHttpResponse, FullHttpRequest>> future)
                throws Exception {
            if (future.isSuccess()) {
                DedicatedHttpClient<FullHttpResponse, FullHttpRequest> dedicatedClient = future.get();
                Future<FullHttpResponse> clientProcessingFuture = dedicatedClient.execute(request, responseHandler);
                processingFinishPromise.setClientProcessingFuture(clientProcessingFuture);
            } else {
                processingFinishPromise.setFailure(future.cause());
            }
        }
    }
}
