package perf.test.utils;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.junit.Test;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Scheduler;
import rx.Subscriber;
import rx.schedulers.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static junit.framework.Assert.*;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class BackendResponse {

    private final long responseKey;
    private final int delay;
    private final int numItems;
    private final int itemSize;
    private final String[] items;
    private final boolean fallback;

    public BackendResponse(long responseKey, int delay, int numItems, int itemSize, String[] items) {
        this(responseKey, delay, numItems, itemSize, items, false);
    }

    public BackendResponse(long responseKey, int delay, int numItems, int itemSize, String[] items, boolean fallback) {
        this.responseKey = responseKey;
        this.delay = delay;
        this.numItems = numItems;
        this.itemSize = itemSize;
        this.items = items;
        this.fallback = fallback;
    }

    public static BackendResponse fromJson(JsonFactory jsonFactory, byte[] content) throws Exception {
        JsonParser parser = jsonFactory.createJsonParser(content);
        return parseBackendResponse(parser);
    }

    public static BackendResponse fromJson(JsonFactory jsonFactory, String json) throws Exception {
        JsonParser parser = jsonFactory.createJsonParser(json);
        return parseBackendResponse(parser);
    }

    public static BackendResponse fromJson(JsonFactory jsonFactory, InputStream inputStream) throws JsonParseException {
        try {
            JsonParser parser = jsonFactory.createJsonParser(inputStream);
            return parseBackendResponse(parser);
        } catch (Exception e) {
            throw new JsonParseException("Failed to parse JSON", e);
        }
    }

    public static Observable<BackendResponse> fromJsonToObservable(final JsonFactory jsonFactory, final String json) {
        return fromJsonToObservable(jsonFactory, json, Schedulers.computation());
    }

    public static Observable<BackendResponse> fromJsonToObservable(final JsonFactory jsonFactory, final String json, Scheduler scheduler) {
        return Observable.create(new OnSubscribe<BackendResponse>() {

            @Override
            public void call(Subscriber<? super BackendResponse> o) {
                try {
                    o.onNext(fromJson(jsonFactory, json));
                    o.onCompleted();
                } catch (Exception e) {
                    o.onError(e);
                }
            }
        }).subscribeOn(scheduler);
    }

    public static BackendResponse parseBackendResponse(JsonParser parser) throws IOException {
        try {
            // Sanity check: verify that we got "Json Object":
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected data to start with an Object");
            }
            long responseKey = 0;
            int delay = 0;
            int numItems = 0;
            int itemSize = 0;
            boolean fallback = false;
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
                } else if(fieldName.equals("fallback")) {
                    fallback = parser.getBooleanValue();
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

            return new BackendResponse(responseKey, delay, numItems, itemSize, items, fallback);
        } finally {
            parser.close();
        }
    }

    public long getResponseKey() {
        return responseKey;
    }

    public int getDelay() {
        return delay;
    }

    public int getNumItems() {
        return numItems;
    }

    public int getItemSize() {
        return itemSize;
    }

    public String[] getItems() {
        return items;
    }

    public boolean isFallback() {
        return fallback;
    }

    @Override
    public String toString() {
        return "BackendResponse{" +
                "responseKey=" + responseKey +
                ", delay=" + delay +
                ", numItems=" + numItems +
                ", itemSize=" + itemSize +
                ", fallback=" + fallback +
                ", items=" + Arrays.toString(items) +
                '}';
    }

    public static class UnitTest {

        @Test
        public void testJsonParse() throws Exception {
            JsonFactory jsonFactory = new JsonFactory();
            BackendResponse r = fromJson(jsonFactory, "{ \"responseKey\": 9999, \"delay\": 50, \"fallback\": false, \"itemSize\": 128, \"numItems\": 2, \"items\": [ \"Lorem\", \"Ipsum\" ]}");
            assertEquals(9999, r.getResponseKey());
            assertEquals(50, r.getDelay());
            assertEquals(128, r.getItemSize());
            assertEquals(2, r.getNumItems());
            assertFalse(r.fallback);
            String[] items = r.getItems();
            assertEquals(2, items.length);
            assertEquals("Lorem", items[0]);
            assertEquals("Ipsum", items[1]);
        }
    }
}
