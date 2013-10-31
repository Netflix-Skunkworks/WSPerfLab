package perf.test.netty.server;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * @author Nitesh Kant
 */
public class RequestProcessingFailedException extends Exception {

    private static final long serialVersionUID = 4587319282977130830L;
    private HttpResponseStatus status;

    public RequestProcessingFailedException(HttpResponseStatus status) {
        super(String.format("Request processing failed, http response status %s", status));
        this.status = status;
    }

    public RequestProcessingFailedException(HttpResponseStatus status, Throwable cause) {
        super(String.format("Request processing failed, http response status %s", status), cause);
        this.status = status;
    }

    public HttpResponseStatus getStatus() {
        return status;
    }
}
