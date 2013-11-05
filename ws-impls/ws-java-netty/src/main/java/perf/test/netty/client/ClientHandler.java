package perf.test.netty.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.PropertyNames;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ClientHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    private final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    public static final int MAX_RETRIES = 3;

    private final long id;
    private final DedicatedClientPool<FullHttpResponse, FullHttpRequest> pool;

    public ClientHandler(DedicatedClientPool<FullHttpResponse, FullHttpRequest> pool, long id) {
        this.pool = pool;
        this.id = id;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {
        AtomicInteger retries = ctx.channel().attr(DedicatedClientPool.RETRY_COUNT_KEY).get();

        if (logger.isDebugEnabled()) {
            logger.debug("Response completed after {} retries", null == retries ? 0 : retries);
        }
        if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
            checkpoint(ctx, "Response completed after retries: " + retries);
        }

        retries.set(0); // Response received so reset retry.

        Promise<FullHttpResponse> completionPromise = ctx.channel().attr(pool.getProcessingCompletePromiseKey()).get();
        response.content().retain();
        completionPromise.setSuccess(response);
        if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
            checkpoint(ctx, "Promise completed.");
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // Handlers are thread-safe i.e. they do not get invoked concurrently by netty, so you can safely assume that
        // error & success do not happen concurrently.
        final int retryCount = ctx.channel().attr(DedicatedClientPool.RETRY_COUNT_KEY).get().incrementAndGet();
        logger.error("Client id: " + id + ". Client handler got an error. Retry count: " + retryCount, cause);

        if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
            checkpoint(ctx, "Exception on client handler" + cause);
        }

        final RequestExecutionPromise<FullHttpResponse> completionPromise = ctx.channel().attr(pool.getProcessingCompletePromiseKey()).get();

        if (retryCount > MAX_RETRIES) {
            checkpoint(ctx, "Retries exhausted.");
            completionPromise.setFailure(cause);
            return;
        }

        Future<DedicatedHttpClient<FullHttpResponse, FullHttpRequest>> clientGetFuture = pool.getClient(ctx.executor());
        clientGetFuture.addListener(
                new GenericFutureListener<Future<DedicatedHttpClient<FullHttpResponse, FullHttpRequest>>>() {
                    @Override
                    public void operationComplete(Future<DedicatedHttpClient<FullHttpResponse, FullHttpRequest>> future)
                            throws Exception {
                        if (future.isSuccess()) {
                            future.get().retry(ctx, retryCount, completionPromise);
                        } else {
                            completionPromise.setFailure(future.cause());
                        }
                    }
                });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
            checkpoint(ctx, "Channel Inactive.");
        }
    }


    private void checkpoint(ChannelHandlerContext ctx, String checkpoint) {
        checkpoint = "ClientId: " + id + ' ' + checkpoint;
        final GenericFutureListener<Future<FullHttpResponse>> responseHandler = ctx.channel().attr(
                pool.getResponseHandlerKey()).get();
        if (null != responseHandler && DedicatedHttpClient.ResponseHandlerWrapper.class.isAssignableFrom(responseHandler.getClass())) {
            @SuppressWarnings("rawtypes")
            DedicatedHttpClient.ResponseHandlerWrapper wrapper =
                    (DedicatedHttpClient.ResponseHandlerWrapper) responseHandler;
            wrapper.getProcessingFinishPromise().checkpoint(checkpoint);
        }
    }
}
