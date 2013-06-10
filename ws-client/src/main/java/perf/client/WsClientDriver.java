package perf.client;

import org.eclipse.jetty.client.api.Result;

/**
 * @author Nitesh Kant
 */
public class WsClientDriver {

    public static void main(String[] args) throws Exception {
        validate(args);

        AsyncIOClient.Builder clientBuilder = new AsyncIOClient.Builder();
        String uri = args[0];
        String concurrentClientsStr = args[1];
        String requests = args[2];

        System.out.println("Using test uri: [" + uri + "]");
        System.out.println("Using concurrent clients: [" + concurrentClientsStr + "]");
        System.out.println("Using total number of requests: [" + requests + "]");

        int concurrentClients = Integer.parseInt(concurrentClientsStr);
        try {
            clientBuilder.withConcurrentClients(concurrentClients);
        } catch (NumberFormatException e) {
            System.err.println("Illegal concurrent clients value: " + concurrentClientsStr+ " should be an integer");
            printUsageAndExit();
        }

        clientBuilder.withMaxConnections(Integer.MAX_VALUE);
        try {
            clientBuilder.withTotalRequests(Long.parseLong(requests));
        } catch (NumberFormatException e) {
            System.err.println("Illegal total requests: " + requests + " should be a long");
            printUsageAndExit();
        }

        clientBuilder.withTestUrl(uri);

        final AsyncIOClient client = clientBuilder.build();
        client.start(new Runnable() {

            @Override
            public void run() {
                TestResult result = client.getResult();
                String resultAsJson = result.toJson();
                System.out.println("****************************** Result **************************************");
                System.out.println(resultAsJson);
                System.out.println("****************************************************************************");
            }
        });
    }

    private static void validate(String[] args) throws IllegalArgumentException {
        if (args.length < 3) {
            printUsageAndExit();
        }
    }

    private static void printUsageAndExit() {
        System.err.println("Usage java perf.client.WsClientDriver <test_uri> <concurrent_clients> <requests>");
        System.exit(-1);
    }
}
