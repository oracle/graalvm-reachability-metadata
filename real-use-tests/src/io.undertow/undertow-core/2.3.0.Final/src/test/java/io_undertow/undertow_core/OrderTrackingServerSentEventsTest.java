/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.server.handlers.sse.ServerSentEventHandler;
import io.undertow.util.PathTemplateMatch;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Server-Sent Events with `ServerSentEventHandler`: per-order event streams opened through a
 * `RoutingHandler` path template, events with ids, names, multi-line data and a retry hint,
 * broadcast to the connections whose path parameter matches, `Last-Event-ID` replay on reconnect,
 * delivery callbacks, query-parameter filtering, and server-initiated shutdown with close tasks.
 *
 * An order-tracking service keeps an event log per order. Browsers subscribe to
 * `/orders/{orderId}/events`; back-office calls to `POST /orders/{orderId}/status` append a status
 * event and push it to every open stream for that order. The client is the JDK `HttpClient`
 * reading the `text/event-stream` body line by line and parsing it the way an `EventSource`
 * would.
 */
public class OrderTrackingServerSentEventsTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    private Undertow server;
    private HttpClient client;
    private URI base;
    private OrderTracker tracker;
    private final List<EventStream> streams = new ArrayList<>();

    @BeforeEach
    void startServer() {
        tracker = new OrderTracker();
        RoutingHandler routes = Handlers.routing()
                .get("/orders/{orderId}/events", tracker.events)
                .post("/orders/{orderId}/status", tracker::updateStatus)
                .post("/orders/{orderId}/close", tracker::closeFeed);
        server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setHandler(routes)
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        base = URI.create("http://127.0.0.1:" + address.getPort());
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(WAIT)
                .build();
    }

    @AfterEach
    void stopServer() {
        streams.forEach(EventStream::close);
        server.stop();
    }

    @Test
    void streamsTheSnapshotAndBroadcastsStatusUpdatesToMatchingOrders() throws Exception {
        EventStream order42 = subscribe("/orders/42/events", null);
        EventStream otherOrder42 = subscribe("/orders/42/events", null);
        EventStream order7 = subscribe("/orders/7/events", null);

        assertEquals("text/event-stream; charset=UTF-8", order42.contentType);
        Event snapshot = order42.next();
        assertEquals("snapshot", snapshot.name());
        assertEquals("0", snapshot.id());
        assertEquals("order 42 has no updates yet", snapshot.data());
        assertEquals(5000L, order42.reconnectionTime);
        assertEquals("snapshot", otherOrder42.next().name());
        assertEquals("order 7 has no updates yet", order7.next().data());

        assertEquals(204, post("/orders/42/status", "packed\nat warehouse B").statusCode());
        Event packed = order42.next();
        assertEquals("status", packed.name());
        assertEquals("1", packed.id());
        assertEquals("packed\nat warehouse B", packed.data());
        assertEquals(packed, otherOrder42.next());
        assertTrue(tracker.delivered.await(WAIT.toSeconds(), TimeUnit.SECONDS));
        assertEquals(List.of("42:1", "42:1"), List.copyOf(tracker.deliveries));

        assertEquals(204, post("/orders/7/status", "cancelled").statusCode());
        Event cancelled = order7.next();
        assertEquals("cancelled", cancelled.data());
        assertEquals("1", cancelled.id());

        assertEquals(404, post("/orders/99/status", "lost").statusCode());
    }

    @Test
    void replaysMissedEventsAfterTheLastEventIdOnReconnect() throws Exception {
        tracker.append("42", "paid");
        tracker.append("42", "packed");
        tracker.append("42", "shipped");

        EventStream fresh = subscribe("/orders/42/events", null);
        assertEquals("snapshot", fresh.next().name());

        EventStream resumed = subscribe("/orders/42/events", "1");
        Event packed = resumed.next();
        assertEquals("2", packed.id());
        assertEquals("packed", packed.data());
        Event shipped = resumed.next();
        assertEquals("3", shipped.id());
        assertEquals("shipped", shipped.data());

        assertEquals(204, post("/orders/42/status", "delivered").statusCode());
        assertEquals("delivered", resumed.next().data());
        Event live = fresh.next();
        assertEquals("4", live.id());
        assertEquals("delivered", live.data());
    }

    @Test
    void filtersEventTypesRequestedThroughTheQueryString() throws Exception {
        EventStream statusOnly = subscribe("/orders/42/events?types=status", null);
        EventStream everything = subscribe("/orders/42/events", null);
        assertEquals("snapshot", everything.next().name());
        assertTrue(tracker.awaitConnections("42", 2), "both streams are registered");

        assertEquals(204, post("/orders/42/status", "paid").statusCode());
        Event first = statusOnly.next();
        assertEquals("status", first.name());
        assertEquals("paid", first.data());
        assertEquals("paid", everything.next().data());
    }

    @Test
    void closesTheFeedFromTheServerAndRunsCloseTasks() throws Exception {
        EventStream feed = subscribe("/orders/42/events", null);
        assertEquals("snapshot", feed.next().name());
        assertEquals(1, tracker.openConnections("42"));

        HttpResponse<String> closed = post("/orders/42/close", "");
        assertEquals(200, closed.statusCode());
        assertEquals("closed 1 stream(s)", closed.body());

        Event farewell = feed.next();
        assertEquals("closing", farewell.name());
        assertEquals("order 42 feed closed by server", farewell.data());
        assertTrue(feed.awaitEnd(), "the event stream ends after shutdown");
        assertTrue(tracker.closed.await(WAIT.toSeconds(), TimeUnit.SECONDS));
        assertEquals(List.of("42"), List.copyOf(tracker.closedOrders));
        assertEquals(0, tracker.openConnections("42"));
    }

    private EventStream subscribe(String path, String lastEventId) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(WAIT)
                .header("Accept", "text/event-stream")
                .GET();
        if (lastEventId != null) {
            request.header("Last-Event-ID", lastEventId);
        }
        HttpResponse<Stream<String>> response = client.send(request.build(), HttpResponse.BodyHandlers.ofLines());
        assertEquals(200, response.statusCode());
        EventStream stream = new EventStream(response);
        streams.add(stream);
        return stream;
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(WAIT)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** One dispatched server-sent event as an `EventSource` would see it. */
    record Event(String id, String name, String data) {
    }

    /** Parses a `text/event-stream` body on its own thread into a queue of events. */
    static final class EventStream {

        private static final Event END = new Event(null, null, null);

        private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
        private final Stream<String> lines;
        private final String contentType;
        private volatile long reconnectionTime = -1;

        EventStream(HttpResponse<Stream<String>> response) {
            this.lines = response.body();
            this.contentType = response.headers().firstValue("Content-Type").orElseThrow();
            Thread reader = new Thread(this::read, "sse-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void read() {
            String id = null;
            String name = null;
            List<String> data = new ArrayList<>();
            try {
                for (String line : (Iterable<String>) lines::iterator) {
                    if (line.isEmpty()) {
                        if (!data.isEmpty()) {
                            events.add(new Event(id, name == null ? "message" : name, String.join("\n", data)));
                        }
                        name = null;
                        data = new ArrayList<>();
                    } else if (!line.startsWith(":")) {
                        int colon = line.indexOf(':');
                        String field = colon < 0 ? line : line.substring(0, colon);
                        String value = colon < 0 ? "" : line.substring(colon + 1);
                        value = value.startsWith(" ") ? value.substring(1) : value;
                        switch (field) {
                            case "id" -> id = value;
                            case "event" -> name = value;
                            case "data" -> data.add(value);
                            case "retry" -> reconnectionTime = Long.parseLong(value);
                            default -> {
                            }
                        }
                    }
                }
            } catch (RuntimeException closedByTest) {
                // The test closed the stream while the reader was blocked on it.
            } finally {
                events.add(END);
            }
        }

        Event next() throws InterruptedException {
            Event event = events.poll(WAIT.toSeconds(), TimeUnit.SECONDS);
            assertNotNull(event, "no event arrived in time");
            assertTrue(event != END, "the stream ended before the next event");
            return event;
        }

        boolean awaitEnd() throws InterruptedException {
            return events.poll(WAIT.toSeconds(), TimeUnit.SECONDS) == END;
        }

        void close() {
            lines.close();
        }
    }

    /** The order-tracking application: an event log per order and its SSE fan-out. */
    static final class OrderTracker {

        final ServerSentEventHandler events = Handlers.serverSentEvents(this::connected);
        final Map<String, List<String>> log = new ConcurrentHashMap<>();
        final List<String> deliveries = new CopyOnWriteArrayList<>();
        final CountDownLatch delivered = new CountDownLatch(2);
        final List<String> closedOrders = new CopyOnWriteArrayList<>();
        final CountDownLatch closed = new CountDownLatch(1);

        private final ServerSentEventConnection.EventCallback deliveryCallback =
                new ServerSentEventConnection.EventCallback() {
                    @Override
                    public void done(ServerSentEventConnection connection, String data, String event, String id) {
                        if ("status".equals(event) && "42".equals(connection.getParameter("orderId"))) {
                            deliveries.add("42:" + id);
                            delivered.countDown();
                        }
                    }

                    @Override
                    public void failed(ServerSentEventConnection connection, String data, String event, String id,
                            IOException e) {
                    }
                };

        private void connected(ServerSentEventConnection connection, String lastEventId) {
            String orderId = connection.getParameter("orderId");
            connection.addCloseTask(closedConnection -> {
                closedOrders.add(closedConnection.getParameter("orderId"));
                closed.countDown();
            });
            Deque<String> types = connection.getQueryParameters().get("types");
            if (types != null && !types.contains("snapshot")) {
                return;
            }
            List<String> history = log.getOrDefault(orderId, List.of());
            if (lastEventId == null) {
                connection.sendRetry(5000);
                String summary = history.isEmpty()
                        ? "order " + orderId + " has no updates yet"
                        : "order " + orderId + " is " + history.get(history.size() - 1);
                connection.send(summary, "snapshot", String.valueOf(history.size()), null);
                return;
            }
            for (int index = Integer.parseInt(lastEventId); index < history.size(); index++) {
                connection.send(history.get(index), "status", String.valueOf(index + 1), null);
            }
        }

        synchronized int append(String orderId, String status) {
            List<String> history = log.computeIfAbsent(orderId, id -> new CopyOnWriteArrayList<>());
            history.add(status);
            return history.size();
        }

        void updateStatus(HttpServerExchange exchange) {
            String orderId = orderId(exchange);
            if (!"42".equals(orderId) && !"7".equals(orderId)) {
                exchange.setStatusCode(StatusCodes.NOT_FOUND);
                exchange.endExchange();
                return;
            }
            exchange.getRequestReceiver().receiveFullString((ex, status) -> {
                String id = String.valueOf(append(orderId, status));
                for (ServerSentEventConnection connection : events.getConnections()) {
                    if (orderId.equals(connection.getParameter("orderId"))) {
                        connection.send(status, "status", id, deliveryCallback);
                    }
                }
                ex.setStatusCode(StatusCodes.NO_CONTENT);
                ex.endExchange();
            }, StandardCharsets.UTF_8);
        }

        void closeFeed(HttpServerExchange exchange) {
            String orderId = orderId(exchange);
            int count = 0;
            for (ServerSentEventConnection connection : events.getConnections()) {
                if (orderId.equals(connection.getParameter("orderId"))) {
                    connection.send("order " + orderId + " feed closed by server", "closing", null, null);
                    connection.shutdown();
                    count++;
                }
            }
            exchange.getResponseSender().send("closed " + count + " stream(s)");
        }

        long openConnections(String orderId) {
            return events.getConnections().stream()
                    .filter(connection -> orderId.equals(connection.getParameter("orderId")))
                    .count();
        }

        boolean awaitConnections(String orderId, long expected) throws InterruptedException {
            long deadline = System.nanoTime() + WAIT.toNanos();
            while (openConnections(orderId) != expected) {
                if (System.nanoTime() > deadline) {
                    return false;
                }
                Thread.sleep(10);
            }
            return true;
        }

        private static String orderId(HttpServerExchange exchange) {
            return exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY).getParameters().get("orderId");
        }
    }
}
