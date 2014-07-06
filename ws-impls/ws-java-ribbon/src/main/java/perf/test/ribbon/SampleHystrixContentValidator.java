package perf.test.ribbon;

import com.netflix.ribbon.ServerError;
import com.netflix.ribbon.UnsuccessfulResponseException;
import com.netflix.ribbon.http.HttpResponseValidator;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

/**
 * @author Tomasz Bak
 */
public class SampleHystrixContentValidator implements HttpResponseValidator {
    @Override
    public void validate(HttpClientResponse<ByteBuf> httpClientResponse) throws UnsuccessfulResponseException, ServerError {
        if (httpClientResponse.getStatus().code() / 100 != 2) {
            throw new UnsuccessfulResponseException("Unexpected HTTP status code received: " + httpClientResponse.getStatus());
        }
    }
}
