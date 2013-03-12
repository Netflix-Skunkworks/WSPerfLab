package perf.test.netty.client;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ClientHandler extends SimpleChannelUpstreamHandler {

    private Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    private String content;
    private int responseStatusCode;
    final Object contentPopulationLock = new Object();

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        NettyClient client = (NettyClient) ctx.getChannel().getAttachment();
        client.release(); // Release the client to be reused, the future has a reference to this handler & hence can get the result.

        // AFTER THIS POINT, thou should not refer to the context.

        HttpResponse response = (HttpResponse) e.getMessage();
        HttpResponseStatus status = response.getStatus();
        responseStatusCode = status.getCode();
        if (status.equals(HttpResponseStatus.OK)) {
            ChannelBuffer responseContent = response.getContent();
            if (responseContent.readable()) {
                content = responseContent.toString(CharsetUtil.UTF_8);
                synchronized (contentPopulationLock) {
                    contentPopulationLock.notify(); /// There is only one thread waiting always unless absuive clients wait on spawned threads.
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        logger.error("Client handler got an error.", e);
    }

    public void reset() {
        content = null;
        responseStatusCode = 0;
    }

    public boolean isDone() {
        return responseStatusCode > 0;
    }

    public boolean isSuccess() {
        return HttpResponseStatus.OK.getCode() == responseStatusCode;
    }

    public int getResponseStatusCode() {
        return responseStatusCode;
    }

    public boolean hasContent() {
        return null != content;
    }

    public String getContent() {
        return content;
    }
}
