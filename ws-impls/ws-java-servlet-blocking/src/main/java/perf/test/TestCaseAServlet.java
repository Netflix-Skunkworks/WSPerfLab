package perf.test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.junit.Test;

/**
 * Servlet implementation class TestServlet
 */
public class TestCaseAServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private final static JsonFactory jsonFactory = new JsonFactory();

    // used for multi-threaded Http requests
    private final PoolingClientConnectionManager cm;
    private final HttpClient httpclient;
    // used for parallel execution of requests
    private final ThreadPoolExecutor executor;

    private final String hostname;

    public TestCaseAServlet() {

        // hostname via properties
        String host = System.getProperty("perf.test.backend.hostname");
        if (host == null) {
            throw new IllegalStateException("The perf.test.backend.hostname property must be set.");
        }
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        hostname = host;

        cm = new PoolingClientConnectionManager();
        // set the limit high so this isn't throttling us while we push to the limit
        cm.setMaxTotal(1000);
        httpclient = new DefaultHttpClient(cm);

        // used for parallel execution
        executor = new ThreadPoolExecutor(200, 1000, 1, TimeUnit.HOURS, new LinkedBlockingQueue<Runnable>());
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        try {
            StringWriter jsonString = new StringWriter();
            JsonGenerator json = jsonFactory.createJsonGenerator(jsonString);

            Object _id = request.getParameter("id");
            if (_id == null) {
                response.getWriter().println("Please provide a numerical 'id' value. It can be a random number (uuid).");
                response.setStatus(500);
                return;
            }
            final long id = Long.parseLong(String.valueOf(_id));

            try {

                /* First 2 requests (A, B) in parallel */
                final Future<String> aResponse = queueGet("/mock.json?numItems=2&itemSize=50&delay=50&id=" + id);
                final Future<String> bResponse = queueGet("/mock.json?numItems=25&itemSize=30&delay=150&id=" + id);

                /* When response A received perform C & D */
                // spawned in another thread so we don't block the ability to B/E to proceed in parallel
                Future<ResponseObject[]> aGroupResponses = executor.submit(new Callable<ResponseObject[]>() {

                    @Override
                    public ResponseObject[] call() throws Exception {
                        String aValue = aResponse.get();
                        ResponseObject aResponse = ResponseObject.fromJson(aValue);
                        final Future<String> cResponse = queueGet("/mock.json?numItems=1&itemSize=5000&delay=80&id=" + aResponse.responseKey);
                        final Future<String> dResponse = queueGet("/mock.json?numItems=1&itemSize=1000&delay=1&id=" + aResponse.responseKey);
                        return new ResponseObject[] { aResponse, ResponseObject.fromJson(cResponse.get()), ResponseObject.fromJson(dResponse.get()) };
                    }

                });

                /* When response B is received perform E */
                String bValue = bResponse.get();
                ResponseObject b = ResponseObject.fromJson(bValue);
                String eValue = get("/mock.json?numItems=100&itemSize=30&delay=40&id=" + b.responseKey);

                ResponseObject e = ResponseObject.fromJson(eValue);

                /*
                 * Parse JSON so we can extract data and combine data into a single response.
                 * 
                 * This simulates what real web-services do most of the time.
                 */
                ResponseObject a = aGroupResponses.get()[0];
                ResponseObject c = aGroupResponses.get()[1];
                ResponseObject d = aGroupResponses.get()[2];

                /*
                 * Compose into JSON:
                 */
                json.writeStartObject();
                // multiplication of C, D, E responseKey
                json.writeNumberField("responseKey", c.responseKey + d.responseKey + e.responseKey);

                // delay values of each response
                json.writeArrayFieldStart("delay");
                writeTuple(json, "a", a.delay);
                writeTuple(json, "b", b.delay);
                writeTuple(json, "c", c.delay);
                writeTuple(json, "d", d.delay);
                writeTuple(json, "e", e.delay);
                json.writeEndArray();

                // itemSize values of each response
                json.writeArrayFieldStart("itemSize");
                writeTuple(json, "a", a.itemSize);
                writeTuple(json, "b", b.itemSize);
                writeTuple(json, "c", c.itemSize);
                writeTuple(json, "d", d.itemSize);
                writeTuple(json, "e", e.itemSize);
                json.writeEndArray();

                // numItems values of each response
                json.writeArrayFieldStart("numItems");
                writeTuple(json, "a", a.numItems);
                writeTuple(json, "b", b.numItems);
                writeTuple(json, "c", c.numItems);
                writeTuple(json, "d", d.numItems);
                writeTuple(json, "e", e.numItems);
                json.writeEndArray();

                // all items from responses
                json.writeArrayFieldStart("items");
                addItemsFromResponse(json, a);
                addItemsFromResponse(json, b);
                addItemsFromResponse(json, c);
                addItemsFromResponse(json, d);
                addItemsFromResponse(json, e);
                json.writeEndArray();

                json.writeEndObject();
                json.close();

                // output to stream
                response.getWriter().write(jsonString.toString());
            } catch (Exception e) {
                // error that needs to be returned
                response.setStatus(500);
                response.getWriter().println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            response.addHeader("server_response_time", String.valueOf((System.currentTimeMillis() - startTime)));
        }
    }

    protected void addItemsFromResponse(JsonGenerator json, ResponseObject a) throws IOException, JsonGenerationException {
        for (String s : a.items) {
            json.writeString(s);
        }
    }

    protected void writeTuple(JsonGenerator json, String name, int value) throws IOException, JsonGenerationException {
        json.writeStartObject();
        json.writeNumberField(name, value);
        json.writeEndObject();
    }

    public Future<String> queueGet(final String url) {
        return executor.submit(new Callable<String>() {

            @Override
            public String call() throws Exception {
                return get(url);
            }

        });
    }

    public String get(String url) {
        HttpGet httpGet = new HttpGet(hostname + url);
        try {
            HttpResponse response = httpclient.execute(httpGet);

            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Failure: " + response.getStatusLine());
            }

            HttpEntity entity = response.getEntity();

            // get response data
            String data = EntityUtils.toString(entity);

            // ensure it is fully consumed
            EntityUtils.consume(entity);

            return data;
        } catch (Exception e) {
            throw new RuntimeException("Failure retrieving: " + url, e);
        } finally {
            httpGet.releaseConnection();
        }
    }

    private static class ResponseObject {
        private final long responseKey;
        private final int delay;
        private final int numItems;
        private final int itemSize;
        private final String[] items;

        private ResponseObject(long responseKey, int delay, int numItems, int itemSize, String[] items) {
            this.responseKey = responseKey;
            this.delay = delay;
            this.numItems = numItems;
            this.itemSize = itemSize;
            this.items = items;
        }

        private static ResponseObject fromJson(String json) throws Exception {
            JsonParser parser = jsonFactory.createJsonParser(json);
            try {
                // Sanity check: verify that we got "Json Object":
                if (parser.nextToken() != JsonToken.START_OBJECT) {
                    throw new IOException("Expected data to start with an Object");
                }
                long responseKey = 0;
                int delay = 0;
                int numItems = 0;
                int itemSize = 0;
                String[] items = null;
                JsonToken current;

                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = parser.getCurrentName();
                    // advance
                    current = parser.nextToken();
                    if (fieldName.equals("responseKey")) {
                        responseKey = parser.getLongValue();
                    } else if (fieldName.equals("delay")) {
                        delay = parser.getIntValue();
                    } else if (fieldName.equals("itemSize")) {
                        itemSize = parser.getIntValue();
                    } else if (fieldName.equals("numItems")) {
                        numItems = parser.getIntValue();
                    } else if (fieldName.equals("items")) {
                        // expect numItems to be populated before hitting this
                        if (numItems == 0) {
                            throw new IllegalStateException("Expected numItems > 0");
                        }
                        items = new String[numItems];
                        if (current == JsonToken.START_ARRAY) {
                            int j = 0;
                            // For each of the records in the array
                            while (parser.nextToken() != JsonToken.END_ARRAY) {
                                items[j++] = parser.getText();
                            }
                        } else {
                            //                            System.out.println("Error: items should be an array: skipping.");
                            parser.skipChildren();
                        }

                    }
                }
                return new ResponseObject(responseKey, delay, numItems, itemSize, items);
            } finally {
                parser.close();
            }
        }
    }

    public static class UnitTest {

        @Test
        public void testJsonParse() throws Exception {
            ResponseObject r = ResponseObject.fromJson("{ \"responseKey\": 9999, \"delay\": 50, \"itemSize\": 128, \"numItems\": 2, \"items\": [ \"Lorem\", \"Ipsum\" ]}");
            assertEquals(9999, r.responseKey);
            assertEquals(50, r.delay);
            assertEquals(128, r.itemSize);
            assertEquals(2, r.numItems);
            assertEquals(2, r.items.length);
            assertEquals("Lorem", r.items[0]);
            assertEquals("Ipsum", r.items[1]);
        }
    }
}
