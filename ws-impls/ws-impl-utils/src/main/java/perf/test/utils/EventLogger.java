package perf.test.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.utils.netty.Sampler;

/**
 * @author mhawthorne
 */
public class EventLogger {

    private static final int SAMPLE_PERCENTAGE = PropertyNames.EventLogSamplePercentage.getValueAsInt();

    private static final Logger LOG = LoggerFactory.getLogger(EventLogger.class);

    public static final void log(String requestId, String eventMsg) {
        if (Sampler.shouldSample(SAMPLE_PERCENTAGE)) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("{} {}", requestId, eventMsg);
            }
        }
    }

}
