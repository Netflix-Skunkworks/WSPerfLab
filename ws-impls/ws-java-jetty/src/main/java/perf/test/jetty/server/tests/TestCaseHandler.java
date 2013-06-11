package perf.test.jetty.server.tests;

import org.codehaus.jackson.JsonFactory;
import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.jetty.PropertyNames;
import perf.test.utils.ServiceResponseBuilder;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Nitesh Kant
 */
public abstract class TestCaseHandler {

    private Logger logger = LoggerFactory.getLogger(TestCaseHandler.class);
    protected final static JsonFactory jsonFactory = new JsonFactory();

    private final String testCaseName;

    protected TestCaseHandler(String testCaseName) {
        this.testCaseName = testCaseName;
    }

    public String getTestCaseName() {
        return testCaseName;
    }

    public void execute(final long startTime, Request baseRequest, HttpServletResponse response,
                        final Continuation continuation) throws Exception {
        String id = baseRequest.getParameter("id");
        if (null == id) {
            logger.error("No test case id found, terminating execution.");
            response.addHeader(PropertyNames.ErrorHeaderName.getValueAsString(), "No test case id found in request.");
            response.setStatus(400);
            return;
        }


        continuation.suspend(response);

        doExecute(id, baseRequest, response, continuation, new Runnable() {

            @Override
            public void run() {
                onComplete(startTime, continuation);
            }

        });
    }

    protected abstract void doExecute(String id, Request baseRequest, HttpServletResponse response,
            Continuation continuation, final Runnable onCompleteHandler) throws Exception;

    protected void onComplete(long startTime, Continuation continuation) {
        if (logger.isDebugEnabled()) {
            logger.debug("Completed jetty continuation: " + continuation);
        }
        HttpServletResponse servletResponse = (HttpServletResponse) continuation.getServletResponse();
        ServiceResponseBuilder.addResponseHeaders(servletResponse, startTime);

        try {
            servletResponse.flushBuffer();
        } catch (IOException e) {
            logger.error("Error while committing response");
        }

        continuation.complete();
    }

    public void dispose() throws Exception {

    }
}
