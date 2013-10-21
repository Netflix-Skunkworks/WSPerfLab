package perf.test.netty.server;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ServerPipelineFactory /*implements ChannelPipelineFactory */{
/*

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();
        pipeline.addFirst("timingStart", new ProcessingTimesStartInterceptor());
        if (PropertyNames.ServerLoggingEnable.getValueAsBoolean()) {
            pipeline.addLast("logger", new LoggingHandler());
        }
        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("aggregator", new HttpChunkAggregator(PropertyNames.ServerChunkSize.getValueAsInt()));
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("timingEnd", new ProcessingTimeEndInterceptor());
        pipeline.addLast("handler", new ServerHandler(PropertyNames.ServerContextPath.getValueAsString()));
        return pipeline;
    }
*/
}
