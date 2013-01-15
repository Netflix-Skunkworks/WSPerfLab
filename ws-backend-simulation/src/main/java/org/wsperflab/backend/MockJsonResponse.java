package org.wsperflab.backend;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.junit.Test;

/**
 * Servlet to handle requests and simulate responses with varying types of payloads depending on request arguments.
 * <p>
 * <ul>
 * <li><b>delay</b> - time in milliseconds to delay response to simulate server-side latency</li>
 * <li><b>itemSize</b> - size in characters desired for each item</li>
 * <li><b>numItems</b> - number of items in a list to return to make the client parse</li>
 * </ul>
 */
public class MockJsonResponse extends HttpServlet {
    private static final long serialVersionUID = 6905727859944036525L;
    private final static JsonFactory jsonFactory = new JsonFactory();

    // long String made of repeating characters 48 to 125 (77 characters)
    private static String RAW_ITEM_LONG;
    private static int MAX_ITEM_LENGTH = 1024 * 50;

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

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        int delay = getParameter(request, "delay", 50); // default to 50ms server-side delay
        int itemSize = getParameter(request, "itemSize", 128); // default to 128 bytes item size (assuming ascii text)
        int numItems = getParameter(request, "numItems", 10); // default to 10 items in a list

        // no more than 100 items
        if (numItems < 1 || numItems > 100) {
            response.getWriter().println("Please choose a 'numItems' value from 1 to 100.");
            response.setStatus(500);
            return;
        }

        // no larger than 50KB per item
        if (itemSize < 1 || itemSize > 1024 * 50) {
            response.getWriter().println("Please choose an 'itemSize' value from 1 to 1024*50 (50KB).");
            response.setStatus(500);
            return;
        }

        // no larger than 60 second delay
        if (delay < 0 || delay > 60000) {
            response.getWriter().println("Please choose a 'delay' value from 0 to 60000 (60 seconds).");
            response.setStatus(500);
            return;
        }

        String json = generateJson(delay, itemSize, numItems);

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            // do nothing
        }

        response.getWriter().write(json);
    }

    protected static String generateJson(int delay, int itemSize, int numItems) throws IOException, JsonGenerationException {
        StringWriter jsonString = new StringWriter();
        JsonGenerator json = jsonFactory.createJsonGenerator(jsonString);

        json.writeStartObject();
        json.writeNumberField("delay", delay);
        if (itemSize > MAX_ITEM_LENGTH) {
            throw new IllegalArgumentException("itemSize can not be larger than: " + MAX_ITEM_LENGTH);
        }
        json.writeNumberField("itemSize", itemSize);
        json.writeNumberField("numItems", numItems);

        json.writeArrayFieldStart("items");
        for (int i = 0; i < numItems; i++) {
            json.writeString(RAW_ITEM_LONG.substring(0, itemSize));
        }
        json.writeEndArray();
        json.writeEndObject();
        json.close();

        return jsonString.toString();
    }

    private static int getParameter(HttpServletRequest request, String key, int defaultValue) {
        Object v = request.getParameter(key);
        if (v == null) {
            return defaultValue;
        } else {
            return Integer.parseInt(String.valueOf(v));
        }
    }

    public static class UnitTest {

        @Test
        public void testJson() throws Exception {
            String json = generateJson(1, 1000, 5);
            assertTrue(json.startsWith("{\"delay\":1,\"itemSize\":1000,\"numItems\":5,\"items\""));
            System.out.println("json: " + json);
        }
    }

}
