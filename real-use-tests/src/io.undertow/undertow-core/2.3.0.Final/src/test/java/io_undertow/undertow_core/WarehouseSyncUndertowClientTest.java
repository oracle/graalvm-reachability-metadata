/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientStatistics;
import io.undertow.client.UndertowClient;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.PathTemplateMatch;
import io.undertow.util.StatusCodes;
import io.undertow.util.StringReadChannelListener;
import io.undertow.util.StringWriteChannelListener;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnio.ChannelListeners;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;

/**
 * Undertow's own client stack, `UndertowClient` with `ClientConnection`, `ClientRequest` and
 * `ClientExchange`, talking to an embedded server over both an HTTP/1.1 listener and an AJP
 * listener.
 *
 * A warehouse-sync agent keeps one long-lived connection to the stock service and issues every
 * call on it: stock lookups with query parameters and custom headers, stock adjustments with a
 * request body streamed through `StringWriteChannelListener`, a streamed stock report sent with
 * chunked transfer encoding, and an unknown path answering 404. The same agent reaches the
 * service through the AJP connector the way a front-end proxy does, by choosing the `ajp://`
 * scheme, which `UndertowClient` resolves to its AJP provider. Keep-alive reuse is shown by the
 * server seeing every request arrive from the connection's own local port, the connection's
 * `ClientStatistics` counts the requests it carried, and a `Connection: close` request makes the
 * server end the connection, which the agent observes through its close listener.
 */
public class WarehouseSyncUndertowClientTest {

    private static final HttpString PEER_PORT = new HttpString("X-Peer-Port");
    private static final HttpString AGENT = new HttpString("X-Sync-Agent");

    private Undertow server;
    private XnioWorker worker;
    private ByteBufferPool bufferPool;
    private URI httpUri;
    private URI ajpUri;
    private Map<String, Integer> stock;

    @BeforeEach
    void startServiceAndClientWorker() throws IOException {
        stock = new ConcurrentHashMap<>();
        stock.put("BOLT-M8", 120);
        stock.put("NUT-M8", 300);
        stock.put("WASHER-M8", 75);

        HttpHandler routes = Handlers.routing()
                .get("/stock/report", new StockReport())
                .get("/stock/{sku}", this::lookup)
                .post("/stock/{sku}/adjustments", this::adjust)
                .setFallbackHandler(exchange -> reply(exchange, StatusCodes.NOT_FOUND, "unknown resource"));
        server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .addAjpListener(0, "127.0.0.1")
                .setHandler(routes)
                .build();
        server.start();
        for (Undertow.ListenerInfo listener : server.getListenerInfo()) {
            int port = ((InetSocketAddress) listener.getAddress()).getPort();
            if ("ajp".equals(listener.getProtcol())) {
                ajpUri = URI.create("ajp://127.0.0.1:" + port);
            } else {
                httpUri = URI.create("http://127.0.0.1:" + port);
            }
        }
        assertTrue(httpUri != null && ajpUri != null, "both listeners must be bound");

        worker = Xnio.getInstance().createWorker(OptionMap.builder()
                .set(Options.WORKER_IO_THREADS, 2)
                .set(Options.WORKER_TASK_CORE_THREADS, 2)
                .set(Options.WORKER_TASK_MAX_THREADS, 2)
                .getMap());
        bufferPool = new DefaultByteBufferPool(false, 8192);
    }

    @AfterEach
    void stopServiceAndClientWorker() throws InterruptedException {
        worker.shutdown();
        assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS), "client worker must terminate");
        server.stop();
        bufferPool.close();
    }

    @Test
    void reusesOneHttpConnectionForLookupsAdjustmentsAndReports() throws Exception {
        ClientConnection connection = connect(httpUri);
        try {
            int localPort = connection.getLocalAddress(InetSocketAddress.class).getPort();

            Reply lookup = send(connection, Methods.GET, "/stock/BOLT-M8?warehouse=north", null);
            assertEquals(StatusCodes.OK, lookup.status());
            assertEquals("BOLT-M8 north 120 agent=nightly-sync", lookup.body());
            assertEquals("text/plain; charset=UTF-8", lookup.headers().getFirst(Headers.CONTENT_TYPE));
            assertEquals(String.valueOf(localPort), lookup.headers().getFirst(PEER_PORT));

            Reply adjusted = send(connection, Methods.POST, "/stock/BOLT-M8/adjustments", "-20");
            assertEquals(StatusCodes.OK, adjusted.status());
            assertEquals("BOLT-M8 100", adjusted.body());
            assertEquals(100, stock.get("BOLT-M8"));
            assertEquals(String.valueOf(localPort), adjusted.headers().getFirst(PEER_PORT));

            Reply report = send(connection, Methods.GET, "/stock/report", null);
            assertEquals(StatusCodes.OK, report.status());
            assertEquals("chunked", report.headers().getFirst(Headers.TRANSFER_ENCODING));
            assertNull(report.headers().getFirst(Headers.CONTENT_LENGTH));
            assertEquals("""
                    BOLT-M8,100
                    NUT-M8,300
                    WASHER-M8,75
                    """, report.body());
            assertEquals(String.valueOf(localPort), report.headers().getFirst(PEER_PORT));

            Reply missing = send(connection, Methods.GET, "/suppliers", null);
            assertEquals(StatusCodes.NOT_FOUND, missing.status());
            assertEquals("unknown resource", missing.body());

            assertTrue(connection.isOpen(), "keep-alive connection must stay open");
            ClientStatistics statistics = connection.getStatistics();
            assertEquals(4, statistics.getRequests());
            assertTrue(statistics.getWritten() > 0, "request bytes must be counted");
            assertTrue(statistics.getRead() > statistics.getWritten(), "responses outweigh requests");
            statistics.reset();
            assertEquals(0, statistics.getRequests());
        } finally {
            connection.close();
        }
    }

    @Test
    void reachesTheSameServiceThroughTheAjpConnector() throws Exception {
        ClientConnection connection = connect(ajpUri);
        try {
            int localPort = connection.getLocalAddress(InetSocketAddress.class).getPort();

            Reply lookup = send(connection, Methods.GET, "/stock/NUT-M8?warehouse=south", null);
            assertEquals(StatusCodes.OK, lookup.status());
            assertEquals("NUT-M8 south 300 agent=nightly-sync", lookup.body());
            assertEquals(String.valueOf(localPort), lookup.headers().getFirst(PEER_PORT));

            Reply adjusted = send(connection, Methods.POST, "/stock/WASHER-M8/adjustments", "25");
            assertEquals(StatusCodes.OK, adjusted.status());
            assertEquals("WASHER-M8 100", adjusted.body());
            assertEquals(100, stock.get("WASHER-M8"));

            Reply report = send(connection, Methods.GET, "/stock/report", null);
            assertEquals(StatusCodes.OK, report.status());
            assertEquals("""
                    BOLT-M8,120
                    NUT-M8,300
                    WASHER-M8,100
                    """, report.body());
            assertEquals(String.valueOf(localPort), report.headers().getFirst(PEER_PORT));

            Reply missing = send(connection, Methods.GET, "/suppliers", null);
            assertEquals(StatusCodes.NOT_FOUND, missing.status());
            assertEquals("unknown resource", missing.body());
            assertTrue(connection.isOpen(), "AJP connection must stay open between requests");
        } finally {
            connection.close();
        }
    }

    @Test
    void serverEndsTheConnectionWhenTheAgentAsksToClose() throws Exception {
        ClientConnection connection = connect(httpUri);
        CountDownLatch closed = new CountDownLatch(1);
        connection.addCloseListener(closedConnection -> closed.countDown());

        Reply last = send(connection, Methods.GET, "/stock/WASHER-M8?warehouse=east", null, Map.of(Headers.CONNECTION, "close"));
        assertEquals(StatusCodes.OK, last.status());
        assertEquals("WASHER-M8 east 75 agent=nightly-sync", last.body());
        assertEquals("close", last.headers().getFirst(Headers.CONNECTION));

        assertTrue(closed.await(10, TimeUnit.SECONDS), "close listener must fire");
        assertFalse(connection.isOpen());
    }

    private void lookup(HttpServerExchange exchange) {
        String sku = sku(exchange);
        Integer quantity = stock.get(sku);
        if (quantity == null) {
            reply(exchange, StatusCodes.NOT_FOUND, "unknown sku " + sku);
            return;
        }
        String warehouse = exchange.getQueryParameters().get("warehouse").getFirst();
        String agent = exchange.getRequestHeaders().getFirst(AGENT);
        reply(exchange, StatusCodes.OK, sku + " " + warehouse + " " + quantity + " agent=" + agent);
    }

    private void adjust(HttpServerExchange exchange) {
        String sku = sku(exchange);
        exchange.getRequestReceiver().receiveFullString((ready, body) -> {
            int quantity = stock.merge(sku, Integer.parseInt(body.trim()), Integer::sum);
            reply(ready, StatusCodes.OK, sku + " " + quantity);
        }, StandardCharsets.UTF_8);
    }

    private static String sku(HttpServerExchange exchange) {
        return exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY).getParameters().get("sku");
    }

    private static void reply(HttpServerExchange exchange, int status, String body) {
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
        exchange.getResponseHeaders().put(PEER_PORT, peerPort(exchange));
        exchange.getResponseSender().send(body, StandardCharsets.UTF_8);
    }

    private static String peerPort(HttpServerExchange exchange) {
        return String.valueOf(exchange.getConnection().getPeerAddress(InetSocketAddress.class).getPort());
    }

    private ClientConnection connect(URI uri) throws IOException {
        IoFuture<ClientConnection> future = UndertowClient.getInstance()
                .connect(uri, worker, bufferPool, OptionMap.create(UndertowOptions.ENABLE_STATISTICS, true));
        assertEquals(IoFuture.Status.DONE, future.await(10, TimeUnit.SECONDS), "connect to " + uri);
        return future.get();
    }

    private static Reply send(ClientConnection connection, HttpString method, String path, String body)
            throws Exception {
        return send(connection, method, path, body, Map.of());
    }

    /// Sends one request on the connection's I/O thread and waits for the full response body.
    private static Reply send(ClientConnection connection, HttpString method, String path, String body,
            Map<HttpString, String> extraHeaders) throws Exception {
        ClientRequest request = new ClientRequest().setMethod(method).setPath(path);
        request.getRequestHeaders().put(Headers.HOST, "stock.internal");
        request.getRequestHeaders().put(AGENT, "nightly-sync");
        extraHeaders.forEach((name, value) -> request.getRequestHeaders().put(name, value));
        if (body != null) {
            request.getRequestHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
            request.getRequestHeaders().put(Headers.CONTENT_LENGTH, body.getBytes(StandardCharsets.UTF_8).length);
        }
        CompletableFuture<Reply> reply = new CompletableFuture<>();
        connection.getIoThread().execute(() -> connection.sendRequest(request, new ReplyCallback(body, reply)));
        return reply.get(10, TimeUnit.SECONDS);
    }

    /// The response as the agent sees it: status, headers and the decoded body.
    private record Reply(int status, HeaderMap headers, String body) {
    }

    /// Writes the request body, if any, then collects the response into a [Reply].
    private static final class ReplyCallback implements ClientCallback<ClientExchange> {

        private final String body;
        private final CompletableFuture<Reply> reply;

        ReplyCallback(String body, CompletableFuture<Reply> reply) {
            this.body = body;
            this.reply = reply;
        }

        @Override
        public void completed(ClientExchange exchange) {
            exchange.setResponseListener(new ClientCallback<>() {
                @Override
                public void completed(ClientExchange answered) {
                    new StringReadChannelListener(answered.getConnection().getBufferPool()) {
                        @Override
                        protected void stringDone(String content) {
                            reply.complete(new Reply(answered.getResponse().getResponseCode(),
                                    answered.getResponse().getResponseHeaders(), content));
                        }

                        @Override
                        protected void error(IOException e) {
                            reply.completeExceptionally(e);
                        }
                    }.setup(answered.getResponseChannel());
                }

                @Override
                public void failed(IOException e) {
                    reply.completeExceptionally(e);
                }
            });
            if (body != null) {
                new StringWriteChannelListener(body, StandardCharsets.UTF_8).setup(exchange.getRequestChannel());
                return;
            }
            try {
                StreamSinkChannel requestChannel = exchange.getRequestChannel();
                requestChannel.shutdownWrites();
                if (!requestChannel.flush()) {
                    requestChannel.getWriteSetter().set(ChannelListeners.<StreamSinkChannel>flushingChannelListener(null, null));
                    requestChannel.resumeWrites();
                }
            } catch (IOException e) {
                reply.completeExceptionally(e);
            }
        }

        @Override
        public void failed(IOException e) {
            reply.completeExceptionally(e);
        }
    }

    /// Streams one CSV line per SKU in blocking mode, so HTTP sends it chunked.
    private final class StockReport implements HttpHandler {

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            if (exchange.isInIoThread()) {
                exchange.dispatch(this);
                return;
            }
            exchange.startBlocking();
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/csv; charset=UTF-8");
            exchange.getResponseHeaders().put(PEER_PORT, peerPort(exchange));
            OutputStream out = exchange.getOutputStream();
            for (Map.Entry<String, Integer> line : new TreeMap<>(stock).entrySet()) {
                out.write((line.getKey() + "," + line.getValue() + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            exchange.endExchange();
        }
    }
}
