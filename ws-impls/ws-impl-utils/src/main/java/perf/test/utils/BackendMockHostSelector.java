package perf.test.utils;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;

/**
 * A class for selecting back-end URLS to talk to, from file /tmp/wsmock_servers.txt, should have one host name per line
 * 
 * @author skatz
 * 
 */
public class BackendMockHostSelector {
    private static final String DEFAULT_SERVER_LOCATION = "/tmp/wsmock_servers.txt";

    private static final ImmutableList<String> hostNames;

    static {
        String backendHostnameOverride = System.getProperty("perf.test.backend.hostname");
        if (backendHostnameOverride != null) {
            System.out.println("BackendMock => property: perf.test.backend.hostname = " + backendHostnameOverride);
            hostNames = ImmutableList.of(backendHostnameOverride);
        } else {
            System.out.println("BackendMock => file: " + DEFAULT_SERVER_LOCATION);
            final ImmutableList.Builder<String> hostNameBuilder = ImmutableList.builder();
            try {
                List<String> hostLines = Files.readAllLines(Paths.get(DEFAULT_SERVER_LOCATION), Charsets.UTF_8);
                for (String line : hostLines) {
                    String trimmedLine = line.trim();
                    hostNameBuilder.add(trimmedLine);
                }
            } catch (Exception exc) {
                throw new RuntimeException("unable to initialize URLs to use for ws_mock_backend", exc);
            }

            hostNames = hostNameBuilder.build();
        }
    }

    /** picks a host at random. Thread safe */
    public static InetSocketAddress getRandomBackendHost() {
        final String host = hostNames.get(ThreadLocalRandom.current().nextInt(hostNames.size()));
        return new InetSocketAddress(host, 8989);
    }

    public static String getBackendMockBaseContext() {
        return "/ws-backend-mock";
    }

    /**
     * Return host + port + base context such as http://10.0.0.1:8989/ws-backend-mock
     */
    public static String getRandomBackendPathPrefix() {
        return getRandomBackendHost() + getBackendMockBaseContext();
    }
}
