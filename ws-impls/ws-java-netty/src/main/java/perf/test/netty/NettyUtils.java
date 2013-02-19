package perf.test.netty;

import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class NettyUtils {

    public static void createErrorResponse(JsonFactory jsonFactory, HttpResponse response, String errorMsg) {
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
        response.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        response.setContent(ChannelBuffers.copiedBuffer(out.toByteArray()));
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json");
    }

}
