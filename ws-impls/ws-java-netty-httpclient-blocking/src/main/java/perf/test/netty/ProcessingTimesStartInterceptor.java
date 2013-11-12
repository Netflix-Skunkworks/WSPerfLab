package perf.test.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ProcessingTimesStartInterceptor extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<Long> START_TIME_ATTR_KEY = new AttributeKey<Long>("requestStartTime");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.channel().attr(START_TIME_ATTR_KEY).set(System.currentTimeMillis());
        super.channelRead(ctx, msg);
    }
}
