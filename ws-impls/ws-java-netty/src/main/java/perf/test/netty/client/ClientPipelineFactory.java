package perf.test.netty.client;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.logging.LoggingHandler;
import perf.test.netty.PropertyNames;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ClientPipelineFactory implements ChannelPipelineFactory {

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();
        if (PropertyNames.ClientLoggingEnable.getValueAsBoolean()) {
            pipeline.addLast("logger", new LoggingHandler());
        }
        pipeline.addLast("codec", new HttpClientCodec());
        pipeline.addLast("aggregator", new HttpChunkAggregator(PropertyNames.ClientChunkSize.getValueAsInt()));
        pipeline.addLast("handler", new ClientHandler());
        return pipeline;
    }
}
