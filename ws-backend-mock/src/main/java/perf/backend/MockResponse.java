package perf.backend;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import rx.Observable;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.schedulers.Schedulers;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Generate responses with varying types of payloads depending on request arguments.
 * <p>
 * <ul>
 * <li><b>delay</b> - time in milliseconds to delay response to simulate server-side latency</li>
 * <li><b>itemSize</b> - size in characters desired for each item</li>
 * <li><b>numItems</b> - number of items in a list to return to make the client parse</li>
 * </ul>
 */
public class MockResponse {
    private final static JsonFactory jsonFactory = new JsonFactory();

    private static final String RAW_ITEM_LONG;
    private static final int MAX_ITEM_LENGTH = 1024 * 50;

    static {
        StringBuilder builder = new StringBuilder(MAX_ITEM_LENGTH);
        String LOREM = "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
        int length = 0;
        while (length < MAX_ITEM_LENGTH) {
            builder.append(LOREM);
            length += LOREM.length();
        }
        RAW_ITEM_LONG = builder.toString();
    }

    private MockResponse() {
    }

    /**
     * @param id       ID from client used to assert correct client/server interaction
     * @param delay    How long to delay delivery to simulate server-side latency
     * @param itemSize Length of each item String.
     * @param numItems Number of items in response.
     * @return String json
     */
    public static Observable<ByteBuf> generateJson(long id, int delay, int itemSize, int numItems) {
        return Observable.create((Subscriber<? super ByteBuf> subscriber) -> {
            Worker worker = Schedulers.computation().createWorker();
            subscriber.add(worker);
            worker.schedule(() -> {
                try {
                    ByteBuf byteBuf = createJsonResponse(id, delay, itemSize, numItems, false);
                    subscriber.onNext(byteBuf);
                    subscriber.onCompleted();
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            }, delay, TimeUnit.MILLISECONDS);
        });
    }

    public static Observable<ByteBuf> generateFallbackJson(long id, int delay, int itemSize, int numItems) {
        try {
            ByteBuf byteBuf = createJsonResponse(id, delay, itemSize, numItems, true);
            return Observable.just(byteBuf);
        } catch (IOException e) {
            // We do not expect to get here
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static ByteBuf createJsonResponse(long id, int delay, int itemSize, int numItems, boolean fallback) throws IOException {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufOutputStream jsonAsBytes = new ByteBufOutputStream(buffer);
        JsonGenerator json = jsonFactory.createJsonGenerator(jsonAsBytes);

        json.writeStartObject();

        // manipulate the ID such that we can know the response is from the server (client will know the logic)
        long responseKey = getResponseKey(id);

        json.writeNumberField("responseKey", responseKey);

        json.writeNumberField("delay", delay);
        if (itemSize > MAX_ITEM_LENGTH) {
            throw new IllegalArgumentException("itemSize can not be larger than: " + MAX_ITEM_LENGTH);
        }
        json.writeNumberField("itemSize", itemSize);
        json.writeNumberField("numItems", numItems);
        json.writeBooleanField("fallback", fallback);

        json.writeArrayFieldStart("items");
        for (int i = 0; i < numItems; i++) {
            json.writeString(RAW_ITEM_LONG.substring(0, itemSize));
        }
        json.writeEndArray();
        json.writeEndObject();
        json.close();
        return jsonAsBytes.buffer();
    }

    /* package */
    static long getResponseKey(long id) {
        return (id / 37 + 5739375) * 7;
    }
}
