package perf.test.utils;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.junit.Test;

import rx.Observable;
import rx.Observable.OnSubscribeFunc;
import rx.Scheduler;
import rx.concurrency.Schedulers;
import rx.subscriptions.Subscriptions;
import rx.Observer;
import rx.Subscription;

import java.io.IOException;

import static junit.framework.Assert.assertEquals;

/**
* @author Nitesh Kant (nkant@netflix.com)
*/
public class BackendResponse {

    private final long responseKey;
    private final int delay;
    private final int numItems;
    private final int itemSize;
    private final String[] items;

    public BackendResponse(long responseKey, int delay, int numItems, int itemSize, String[] items) {
        this.responseKey = responseKey;
        this.delay = delay;
        this.numItems = numItems;
        this.itemSize = itemSize;
        this.items = items;
    }

    public static BackendResponse fromJson(JsonFactory jsonFactory, byte[] content) throws Exception {
        JsonParser parser = jsonFactory.createJsonParser(content);
        return paseBackendResponse(parser);
    }

    public static BackendResponse fromJson(JsonFactory jsonFactory, String json) throws Exception {
        JsonParser parser = jsonFactory.createJsonParser(json);
        return paseBackendResponse(parser);
    }
    
    public static Observable<BackendResponse> fromJsonToObservable(final JsonFactory jsonFactory, final String json) {
        return fromJsonToObservable(jsonFactory, json, Schedulers.threadPoolForComputation());
    }
    
    public static Observable<BackendResponse> fromJsonToObservable(final JsonFactory jsonFactory, final String json, Scheduler scheduler)  {
        return Observable.create(new OnSubscribeFunc<BackendResponse>() {

            @Override
            public Subscription onSubscribe(Observer<? super BackendResponse> o) {
                try {
                    o.onNext(fromJson(jsonFactory, json));
                    o.onCompleted();
                } catch (Exception e) {
                    o.onError(e);
                }
                return Subscriptions.empty();
            }
        }).subscribeOn(scheduler);
    }

    public static BackendResponse paseBackendResponse(JsonParser parser) throws IOException {
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
            return new BackendResponse(responseKey, delay, numItems, itemSize, items);
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

    public static class UnitTest {

        @Test
        public void testJsonParse() throws Exception {
            JsonFactory jsonFactory = new JsonFactory();
            BackendResponse r = BackendResponse.fromJson(jsonFactory, "{ \"responseKey\": 9999, \"delay\": 50, \"itemSize\": 128, \"numItems\": 2, \"items\": [ \"Lorem\", \"Ipsum\" ]}");
            assertEquals(9999, r.getResponseKey());
            assertEquals(50, r.getDelay());
            assertEquals(128, r.getItemSize());
            assertEquals(2, r.getNumItems());
            String[] items = r.getItems();
            assertEquals(2, items.length);
            assertEquals("Lorem", items[0]);
            assertEquals("Ipsum", items[1]);
        }
    }
}
