package perf.test.ribbon;

import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.ribbon.hystrix.FallbackHandler;
import io.netty.buffer.ByteBuf;
import perf.backend.MockResponse;
import rx.Observable;

import java.util.Map;

/**
 * @author Tomasz Bak
 */
public class DefaultFallbackHandler implements FallbackHandler<ByteBuf> {

    @Override
    public Observable<ByteBuf> getFallback(HystrixExecutableInfo<?> hystrixExecutableInfo, Map<String, Object> stringObjectMap) {
        return MockResponse.generateFallbackJson(
                (Long) stringObjectMap.get("id"),
                (Integer) stringObjectMap.get("delay"),
                (Integer) stringObjectMap.get("itemSize"),
                (Integer) stringObjectMap.get("numItems")
        );
    }
}
