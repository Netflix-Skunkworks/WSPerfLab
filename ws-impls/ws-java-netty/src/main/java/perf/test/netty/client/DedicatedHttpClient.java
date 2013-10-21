package perf.test.netty.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;

import java.util.concurrent.atomic.AtomicInteger;

import static perf.test.netty.client.HttpClient.ClientResponseHandler;

/**
 * @author Nitesh Kant
 */
class DedicatedHttpClient<T, R extends HttpRequest> {

    private final Channel channel;
    private final String host;
    private final DedicatedClientPool<T, R> owningPool;

    DedicatedHttpClient(Channel channel, String host, DedicatedClientPool<T, R> owningPool) {
        this.channel = channel;
        this.host = host;
        this.owningPool = owningPool;
    }

    Future<T> execute(R request, final ClientResponseHandler<T> responseHandler) {
        return executeRequest(request, new ResponseHandlerWrapper<T>(request, responseHandler), 0);
    }

    Future<T> retry(final ChannelHandlerContext failedContext, int retryCount) {
        ResponseHandlerWrapper<T> handler = (ResponseHandlerWrapper<T>) failedContext.channel().attr(owningPool.getResponseHandlerKey()).get();
        return executeRequest(handler.request, handler, retryCount);
    }

    private Future<T> executeRequest(R request, ResponseHandlerWrapper<T> responseHandler, int retryCount) {
        request.headers().set(HttpHeaders.Names.HOST, host);
        channel.attr(DedicatedClientPool.RETRY_COUNT_KEY).setIfAbsent(new AtomicInteger(retryCount));
        channel.attr(owningPool.getResponseHandlerKey()).set(responseHandler);
        ChannelPromise promise = channel.newPromise();
        channel.writeAndFlush(request, promise);
        DefaultPromise<T> processingCompletePromise = new RequestProcessingPromise(channel, promise);
        channel.attr(owningPool.getProcessingCompletePromiseKey()).set(processingCompletePromise);
        return processingCompletePromise;
    }

    public boolean isActive() {
        return channel.isActive();
    }

    class ResponseHandlerWrapper<T> implements ClientResponseHandler<T> {

        private final R request;
        private final ClientResponseHandler<T> actualHandler;

        ResponseHandlerWrapper(R request, ClientResponseHandler<T> actualHandler) {
            this.request = request;
            this.actualHandler = actualHandler;
        }

        @Override
        public void onComplete(T response) {
            owningPool.returnClient(DedicatedHttpClient.this);
            actualHandler.onComplete(response);
        }

        @Override
        public void onError(Throwable throwable) {
            owningPool.returnClient(DedicatedHttpClient.this);
            actualHandler.onError(throwable);
        }
    }

    private class RequestProcessingPromise extends DefaultPromise<T> {

        private final ChannelPromise sendRequestPromise;

        public RequestProcessingPromise(Channel channel, ChannelPromise sendRequestPromise) {
            super(channel.eventLoop());
            this.sendRequestPromise = sendRequestPromise;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (sendRequestPromise.isCancellable()) {
                sendRequestPromise.cancel(mayInterruptIfRunning);
            }
            return super.cancel(mayInterruptIfRunning);
        }
    }
}
