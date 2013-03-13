package perf.test.netty.server;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.utils.ServiceResponseBuilder;

import java.util.Map;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ProcessingTimeEndInterceptor extends SimpleChannelDownstreamHandler {

    private Logger logger = LoggerFactory.getLogger(ProcessingTimeEndInterceptor.class);

    @Override
    public void writeRequested(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        try {
            final Long startTime = (Long) ctx.getChannel().getAttachment();
            try {
                Object msg = e.getMessage();
                if (msg instanceof HttpMessage) {
                    HttpMessage message = (HttpMessage) msg;
                    Map<String, String> headers = ServiceResponseBuilder.getPerfResponseHeaders(startTime);
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        message.addHeader(entry.getKey(), entry.getValue());
                    }
                }
            } catch (Exception ex) {
                logger.error("Error occurred while setting processing end times.", ex);
            }
        } finally {
            ctx.sendDownstream(e);
        }
    }
}
