package perf.test.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import perf.test.utils.EventLogger;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ProcessingTimesStartInterceptor extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<Long> START_TIME_ATTR_KEY = new AttributeKey<Long>("requestStartTime");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.channel().attr(START_TIME_ATTR_KEY).set(System.currentTimeMillis());

        // initializes request id
        final SourceRequestState sourceReqState = SourceRequestState.instance();
        sourceReqState.initRequest(ctx.channel());
        final String requestId = sourceReqState.getRequestId(ctx.channel());

        EventLogger.log(requestId, "channel-start");
        super.channelRead(ctx, msg);
    }
}
