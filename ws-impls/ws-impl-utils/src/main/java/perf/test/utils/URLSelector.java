package perf.test.utils;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;

/** A class for selecting back-end URLS to talk to, from file /tmp/wsmock_servers.txt, should have one host name per line
 * @author skatz
 *
 */
public class URLSelector {
    private static final String DEFAULT_SERVER_LOCATION = "/tmp/wsmock_servers.txt";

    private static final ImmutableList<String> hostNames;

    private static final ThreadLocal<Random> rnd = new ThreadLocal<Random>() {
        @Override
        protected Random initialValue() {
            return new Random();
        }
    };

    static {
        final ImmutableList.Builder<String> hostNameBuilder = ImmutableList.builder();
        try {
            Iterable<String> hostLines = Files.readAllLines(Paths.get(DEFAULT_SERVER_LOCATION), Charsets.UTF_8);
            for (String line : hostLines) {
                String trimmedLine = line.trim();
                hostNameBuilder.add(trimmedLine);
            }
        }
        catch (Exception exc) {
            throw new RuntimeException("unable to initialize URLs to use for ws_mock_backend", exc);
        }
        hostNames = hostNameBuilder.build();
    }

    /** picks a host at random.  Thread safe*/
    public static String chooseHost() {
        return hostNames.get(rnd.get().nextInt(hostNames.size()));
    }
}
