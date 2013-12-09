package perf.test.netty.server;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import perf.test.netty.PropertyNames;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Nitesh Kant
 */
public class RequestProcessingPromise extends DefaultPromise<FullHttpResponse> {

    private final ConcurrentLinkedQueue<String> checkpoints = new ConcurrentLinkedQueue<String>();
    private String testCaseId;

    public RequestProcessingPromise(EventExecutor eventExecutor) {
        super(eventExecutor);
    }

    public void checkpoint(String checkpoint) {
        if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
            checkpoints.add(checkpoint);
        }
    }

    public void populateTrace(StringBuilder traceBuilder) {
        traceBuilder.append('\n');
        traceBuilder.append("****************************************");
        traceBuilder.append("Trace Id: ").append(testCaseId).append('\n');
        for (String checkpoint : checkpoints) {
            traceBuilder.append("->");
            traceBuilder.append(checkpoint);
        }
        traceBuilder.append("****************************************");
    }

    public void setTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
    }
}
