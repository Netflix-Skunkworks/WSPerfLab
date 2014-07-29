package perf.test.ribbon;

import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.proxy.annotation.Http;
import com.netflix.ribbon.proxy.annotation.Http.HttpMethod;
import com.netflix.ribbon.proxy.annotation.Hystrix;
import com.netflix.ribbon.proxy.annotation.TemplateName;
import com.netflix.ribbon.proxy.annotation.Var;
import io.netty.buffer.ByteBuf;

/**
 * Maps mock backend request to Java interface.
 *
 * @author Tomasz Bak
 */
public interface MockBackendService {

    @TemplateName("test")
    @Http(method = HttpMethod.GET, uriTemplate = "/mock.json?numItems={numItems}&itemSize={itemSize}&delay={delay}&id={id}")
    @Hystrix(fallbackHandler = DefaultFallbackHandler.class, validator = SampleHystrixContentValidator.class)
    RibbonRequest<ByteBuf> request(@Var("numItems") Integer numItems, @Var("itemSize") Integer itemSize, @Var("delay") Integer delay, @Var("id") Long id);
}
