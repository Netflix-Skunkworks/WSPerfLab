package perf.test.netty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author mhawthorne
 */
public class EventLogger {

    private static final Logger LOG = LoggerFactory.getLogger(EventLogger.class);

    public static final void log(String requestId, String eventMsg) {
        LOG.debug("{} {}", requestId, eventMsg);
    }

}
