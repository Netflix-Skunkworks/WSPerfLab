package perf.test.jetty.server;

import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.jetty.PropertyNames;
import perf.test.jetty.server.tests.TestCaseHandler;
import perf.test.jetty.server.tests.TestRegistry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
* @author Nitesh Kant
*/
public class ServerHandler extends AbstractHandler {

    private Logger logger = LoggerFactory.getLogger(ServerHandler.class);

    ServerHandler() throws Exception {
        TestRegistry.init();
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request,
                       HttpServletResponse response) throws IOException, ServletException {

        Continuation continuation = ContinuationSupport.getContinuation(request);

        if (continuation.isSuspended()) {
            return; // Looks like jetty gives callbacks even when suspended, need to see more into why.
        }
        final long startTime = System.currentTimeMillis();

        String contextPath = PropertyNames.ServerContextPath.getValueAsString();
        if (!target.startsWith(contextPath)) {
            return;
        }

        try {
            HttpURI uri = baseRequest.getUri();
            String path = uri.getPath();
            String testCasePath = path.substring(contextPath.length());

            TestCaseHandler handler = TestRegistry.getHandler(testCasePath);

            if (null != handler) {
                continuation.setTimeout(PropertyNames.TestCaseExecutionTimeoutMs.getValueAsInt());
                try {
                    handler.execute(startTime, baseRequest, response, continuation);
                } catch (Exception e) {
                    logger.error("Error while executing test case.", e);
                    response.setStatus(500);
                    response.addHeader(PropertyNames.ErrorHeaderName.getValueAsString(), "Test case failed: " + e.getMessage());
                    continuation.complete();
                }
            } else {
                logger.error("Unknown testcase: " + testCasePath);
                response.setStatus(404);
            }
        } finally {
            baseRequest.setHandled(true);
        }
    }

    @Override
    protected void doStop() throws Exception {
        TestRegistry.shutdown();
        super.doStop();
    }
}
