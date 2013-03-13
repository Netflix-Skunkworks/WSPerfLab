package perf.test.utils;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ServiceResponseBuilder {

    public static ByteArrayOutputStream buildTestAResponse(JsonFactory jsonFactory,
            BackendResponse responseA, BackendResponse responseB,
            BackendResponse responseC, BackendResponse responseD,
            BackendResponse responseE) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JsonGenerator jsonGenerator = jsonFactory.createJsonGenerator(bos);

        jsonGenerator.writeStartObject();
        // multiplication of C, D, E responseKey
        jsonGenerator.writeNumberField("responseKey", responseC.getResponseKey() + responseD.getResponseKey() +
                responseE.getResponseKey());

        // delay values of each response
        jsonGenerator.writeArrayFieldStart("delay");
        writeTuple(jsonGenerator, "a", responseA.getDelay());
        writeTuple(jsonGenerator, "b", responseB.getDelay());
        writeTuple(jsonGenerator, "c", responseC.getDelay());
        writeTuple(jsonGenerator, "d", responseD.getDelay());
        writeTuple(jsonGenerator, "e", responseE.getDelay());
        jsonGenerator.writeEndArray();

        // itemSize values of each response
        jsonGenerator.writeArrayFieldStart("itemSize");
        writeTuple(jsonGenerator, "a", responseA.getItemSize());
        writeTuple(jsonGenerator, "b", responseB.getItemSize());
        writeTuple(jsonGenerator, "c", responseC.getItemSize());
        writeTuple(jsonGenerator, "d", responseD.getItemSize());
        writeTuple(jsonGenerator, "e", responseE.getItemSize());
        jsonGenerator.writeEndArray();

        // numItems values of each response
        jsonGenerator.writeArrayFieldStart("numItems");
        writeTuple(jsonGenerator, "a", responseA.getNumItems());
        writeTuple(jsonGenerator, "b", responseB.getNumItems());
        writeTuple(jsonGenerator, "c", responseC.getNumItems());
        writeTuple(jsonGenerator, "d", responseD.getNumItems());
        writeTuple(jsonGenerator, "e", responseE.getNumItems());
        jsonGenerator.writeEndArray();

        // all items from responses
        jsonGenerator.writeArrayFieldStart("items");
        addItemsFromResponse(jsonGenerator, responseA);
        addItemsFromResponse(jsonGenerator, responseB);
        addItemsFromResponse(jsonGenerator, responseC);
        addItemsFromResponse(jsonGenerator, responseD);
        addItemsFromResponse(jsonGenerator, responseE);
        jsonGenerator.writeEndArray();

        jsonGenerator.writeEndObject();
        jsonGenerator.close();

        return bos;
    }

    private static void addItemsFromResponse(JsonGenerator jsonGenerator, BackendResponse a) throws IOException {
        for (String s : a.getItems()) {
            jsonGenerator.writeString(s);
        }
    }

    private static void writeTuple(JsonGenerator jsonGenerator, String name, int value) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeNumberField(name, value);
        jsonGenerator.writeEndObject();
    }

    private static final OperatingSystemMXBean osStats = ManagementFactory.getOperatingSystemMXBean();

    /**
     * Add various headers used for logging and statistics.
     */
    public static void addResponseHeaders(HttpServletResponse response, long startTime) {
        Map<String, String> perfResponseHeaders = getPerfResponseHeaders(startTime);
        for (Map.Entry<String, String> entry : perfResponseHeaders.entrySet()) {
            response.addHeader(entry.getKey(), entry.getValue());
        }
    }

    public static Map<String, String> getPerfResponseHeaders(long startTime) {
        Map<String, String> toReturn = new HashMap<String, String>();
        toReturn.put("server_response_time", String.valueOf((System.currentTimeMillis() - startTime)));

        toReturn.put("os_arch", osStats.getArch());
        toReturn.put("os_name", osStats.getName());
        toReturn.put("os_version", osStats.getVersion());
        toReturn.put("jvm_version", System.getProperty("java.runtime.version"));
        // per core load average
        int cores = Runtime.getRuntime().availableProcessors();
        double loadAverage = osStats.getSystemLoadAverage();
        double loadAveragePerCore = loadAverage / cores;
        String l = String.valueOf(loadAveragePerCore);
        if (l.length() > 4) {
            l = l.substring(0, 4);
        }
        toReturn.put("load_avg_per_core", l);
        return toReturn;
    }
}
