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
import io.netty.util.concurrent.GenericFutureListener;
import perf.test.netty.PropertyNames;

import java.util.concurrent.atomic.AtomicInteger;

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

    Future<T> execute(R request, HttpClientImpl.RequestProcessingPromise processingFinishPromise) {
        return executeRequest(request, new ResponseHandlerWrapper<T>(request, processingFinishPromise), 0);
    }

    Future<T> retry(final ChannelHandlerContext failedContext, int retryCount) {
        ResponseHandlerWrapper<T> handler = (ResponseHandlerWrapper<T>) failedContext.channel().attr(owningPool.getResponseHandlerKey()).get();
        return executeRequest(handler.request, handler, retryCount);
    }

    private Future<T> executeRequest(R request, final ResponseHandlerWrapper<T> responseHandler, int retryCount) {
        request.headers().set(HttpHeaders.Names.HOST, host);
        channel.attr(DedicatedClientPool.RETRY_COUNT_KEY).setIfAbsent(new AtomicInteger(retryCount));
        channel.attr(owningPool.getResponseHandlerKey()).set(responseHandler);
        ChannelPromise promise = channel.newPromise();
        channel.writeAndFlush(request, promise).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
                        responseHandler.processingFinishPromise.checkpoint("Write success.");
                    }
                } else {
                    responseHandler.processingFinishPromise.tryFailure(future.cause()); // TODO: See if we can retry.
                    if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
                        responseHandler.processingFinishPromise.checkpoint("Write failed." + future.cause());
                    }
                }
            }
        });
        responseHandler.processingFinishPromise.checkpoint("Request Written. Retry count: " + retryCount);
        DefaultPromise<T> processingCompletePromise = new RequestProcessingPromise(channel, promise);
        channel.attr(owningPool.getProcessingCompletePromiseKey()).set(processingCompletePromise);
        processingCompletePromise.addListener(responseHandler);
        return processingCompletePromise;
    }

    public boolean isActive() {
        return channel.isActive();
    }

    public class ResponseHandlerWrapper<T> implements GenericFutureListener<Future<T>> {

        private final R request;
        private final HttpClientImpl.RequestProcessingPromise processingFinishPromise;

        ResponseHandlerWrapper(R request, HttpClientImpl.RequestProcessingPromise processingFinishPromise) {
            this.request = request;
            this.processingFinishPromise = processingFinishPromise;
        }

        @Override
        public void operationComplete(Future<T> future) throws Exception {
            owningPool.returnClient(DedicatedHttpClient.this);
        }

        public HttpClientImpl.RequestProcessingPromise getProcessingFinishPromise() {
            return processingFinishPromise;
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
