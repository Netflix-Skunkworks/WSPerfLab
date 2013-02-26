package perf.test.netty;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ProcessingTimesStartInterceptor extends SimpleChannelUpstreamHandler {

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        ctx.getChannel().setAttachment(System.currentTimeMillis());
        super.handleUpstream(ctx, e);
    }
}
