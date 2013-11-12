package perf.test.netty.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.ProcessingTimesStartInterceptor;
import perf.test.utils.ServiceResponseBuilder;

import java.util.Map;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ProcessingTimeEndInterceptor extends ChannelOutboundHandlerAdapter {

    private final Logger logger = LoggerFactory.getLogger(ProcessingTimeEndInterceptor.class);

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            final Long startTime = ctx.channel().attr(ProcessingTimesStartInterceptor.START_TIME_ATTR_KEY).get();
            if (null != startTime) {
                try {
                    if (msg instanceof HttpMessage) {
                        HttpMessage message = (HttpMessage) msg;
                        Map<String, String> headers = ServiceResponseBuilder.getPerfResponseHeaders(startTime);
                        for (Map.Entry<String, String> entry : headers.entrySet()) {
                            message.headers().add(entry.getKey(), entry.getValue());
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Error occurred while setting processing end times.", ex);
                }
            }
        } finally {
            ctx.write(msg, promise);
        }
    }
}
