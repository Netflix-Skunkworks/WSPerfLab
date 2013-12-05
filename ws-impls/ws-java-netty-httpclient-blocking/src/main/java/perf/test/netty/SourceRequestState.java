package perf.test.netty;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.UUID;

/**
 * @author mhawthorne
 */
public class SourceRequestState {

    private static final AttributeKey<String> REQUEST_UUID = new AttributeKey("request_uuid");

    private static final SourceRequestState INSTANCE = new SourceRequestState();

    public static SourceRequestState instance() {
        return INSTANCE;
    }

    private SourceRequestState() {}

    public void initRequest(Channel channel) {
        channel.attr(SourceRequestState.REQUEST_UUID).set(UUID.randomUUID().toString());
    }

    public void endRequest(Channel channel) {}

    public String getRequestId(Channel channel) {
        return channel.attr(SourceRequestState.REQUEST_UUID).get();
    }

}
