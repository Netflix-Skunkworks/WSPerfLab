package perf.test.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class NettyUtils {

    public static void createErrorResponse(JsonFactory jsonFactory, FullHttpResponse response, String errorMsg) {
        createErrorResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, jsonFactory, response, errorMsg);
    }

    public static void createErrorResponse(HttpResponseStatus responseStatus, JsonFactory jsonFactory, FullHttpResponse response, String errorMsg) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator jsonGenerator;
        try {
            jsonGenerator = jsonFactory.createJsonGenerator(out, JsonEncoding.UTF8);
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("Error", errorMsg);
            jsonGenerator.writeEndObject();
            jsonGenerator.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        response.setStatus(responseStatus);
        response.content().writeBytes(Unpooled.copiedBuffer(out.toByteArray()));
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/json");
    }

    public static void sendResponse(Channel channel, boolean keepAlive, JsonFactory jsonFactory, FullHttpResponse response) {

        if (keepAlive) {
            // Add 'Content-Length' header only for a keep-alive connection.
            response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, response.content().readableBytes());
            // Add keep alive header as per:
            // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
            response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }

        ChannelFuture writeFuture = channel.writeAndFlush(response);

        if (!keepAlive) {
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
