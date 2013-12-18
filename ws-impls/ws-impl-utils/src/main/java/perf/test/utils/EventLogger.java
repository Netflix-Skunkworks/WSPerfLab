package perf.test.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.utils.netty.Sampler;

/**
 * @author mhawthorne
 */
public class EventLogger {

    private static final Logger LOG = LoggerFactory.getLogger(EventLogger.class);

    private static final int SAMPLE_PERCENTAGE;

    static {
        SAMPLE_PERCENTAGE = PropertyNames.EventLogSamplePercentage.getValueAsInt();
        LOG.debug(PropertyNames.EventLogSamplePercentage.getPropertyName() + ": " + SAMPLE_PERCENTAGE);
    }

    public static final void log(String requestId, String eventMsg) {
        if (Sampler.shouldSample(requestId.hashCode(), SAMPLE_PERCENTAGE)) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("{} {}", requestId, eventMsg);
            }
        }
    }

}
