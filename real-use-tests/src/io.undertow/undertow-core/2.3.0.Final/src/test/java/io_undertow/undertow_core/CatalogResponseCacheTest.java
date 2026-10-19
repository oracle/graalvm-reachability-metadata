/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.cache.CacheHandler;
import io.undertow.server.handlers.cache.CachedHttpRequest;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.cache.ResponseCache;
import io.undertow.util.DateUtils;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * In-memory response caching with `CacheHandler` over a `DirectBufferCache`: handlers that ask
 * the exchange's `ResponseCache` to serve a stored response before rendering, admission of hot
 * entries, `HEAD` answered from the stored length, one entry per negotiated content type,
 * `304 Not Modified` answered from the cached `ETag` and `Last-Modified` validators, new
 * validators bypassing a stale entry, purging entries by path, and the responses the cache never
 * stores: `POST` bodies and chunked streams.
 *
 * A storefront serves its product catalog as JSON or CSV, a promotional banner, a streamed price
 * ticker and a price quote endpoint from an embedded Undertow server. Every renderer counts its
 * invocations, so the tests observe how often the application actually did the work behind a
 * response. The server runs a single IO thread: a response is copied into the cache on the thread
 * that writes it, so the next request is always looked up after the previous one was stored.
 */
public class CatalogResponseCacheTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /// `DirectBufferCache` allocates buffers for a key on every fifth lookup of it, so a response
    /// is rendered this many times before the cache holds it and serves it from memory.
    private static final int RENDERS_BEFORE_CACHED = 5;

    private Undertow server;
    private HttpClient client;
    private URI base;
    private Storefront storefront;

    @BeforeEach
    void startServer() {
        DirectBufferCache cache = new DirectBufferCache(1024, 16, 4 * 1024 * 1024);
        storefront = new Storefront(cache);
        RoutingHandler routes = Handlers.routing()
                .get("/catalog", storefront::catalog)
                .add(Methods.HEAD, "/catalog", storefront::catalog)
                .get("/banner", storefront::banner)
                .get("/ticker", storefront::ticker)
                .post("/quote", storefront::quote)
                .post("/admin/catalog", storefront::publishCatalog)
                .post("/admin/banner", storefront::publishBanner)
                .post("/admin/purge", storefront::purge);
        server = Undertow.builder()
                .setIoThreads(1)
                .addHttpListener(0, "127.0.0.1")
                .setHandler(new CacheHandler(cache, routes))
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        base = URI.create("http://127.0.0.1:" + address.getPort());
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void servesAHotCatalogFromMemoryOnceItIsAdmitted() throws Exception {
        List<HttpResponse<String>> responses = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            responses.add(get("/catalog", "Accept", "application/json"));
        }

        assertEquals(RENDERS_BEFORE_CACHED, storefront.renders("catalog.json"));
        String first = responses.get(0).body();
        for (HttpResponse<String> response : responses) {
            assertEquals(200, response.statusCode());
            assertEquals(first, response.body());
            assertEquals("application/json", response.headers().firstValue("Content-Type").orElseThrow());
            assertEquals(String.valueOf(first.length()),
                    response.headers().firstValue("Content-Length").orElseThrow());
        }
        JsonNode catalog = JSON.readTree(first);
        assertEquals(1, catalog.get("revision").asInt());
        assertEquals("TEA-01", catalog.get("products").get(0).get("sku").asText());

        HttpResponse<String> head = send("HEAD", "/catalog", null, "Accept", "application/json");
        assertEquals(200, head.statusCode());
        assertEquals(String.valueOf(first.length()), head.headers().firstValue("Content-Length").orElseThrow());
        assertEquals("", head.body());
        assertEquals(RENDERS_BEFORE_CACHED, storefront.renders("catalog.json"));
    }

    @Test
    void keepsOneEntryPerNegotiatedContentType() throws Exception {
        List<String> jsonBodies = new ArrayList<>();
        List<String> csvBodies = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            HttpResponse<String> json = get("/catalog", "Accept", "application/json");
            assertEquals("application/json", json.headers().firstValue("Content-Type").orElseThrow());
            jsonBodies.add(json.body());
            HttpResponse<String> csv = get("/catalog", "Accept", "text/csv");
            assertEquals("text/csv", csv.headers().firstValue("Content-Type").orElseThrow());
            csvBodies.add(csv.body());
        }

        assertEquals(RENDERS_BEFORE_CACHED, storefront.renders("catalog.json"));
        assertEquals(RENDERS_BEFORE_CACHED, storefront.renders("catalog.csv"));
        assertTrue(jsonBodies.stream().allMatch(jsonBodies.get(0)::equals));
        assertTrue(csvBodies.stream().allMatch(csvBodies.get(0)::equals));
        assertEquals("""
                sku,name,price
                TEA-01,Sencha,12.50
                MUG-02,Stoneware mug,18.00
                """, csvBodies.get(0));
    }

    @Test
    void answersConditionalRequestsFromTheCachedValidators() throws Exception {
        HttpResponse<String> full = warmUp("/catalog");
        String etag = full.headers().firstValue("ETag").orElseThrow();
        String lastModified = full.headers().firstValue("Last-Modified").orElseThrow();
        assertEquals("\"catalog-1\"", etag);
        assertEquals("Fri, 01 Mar 2024 09:00:00 GMT", lastModified);

        HttpResponse<String> byEtag = get("/catalog", "Accept", "application/json", "If-None-Match", etag);
        assertEquals(304, byEtag.statusCode());
        assertEquals("", byEtag.body());

        HttpResponse<String> byDate = get("/catalog", "Accept", "application/json",
                "If-Modified-Since", lastModified);
        assertEquals(304, byDate.statusCode());
        assertEquals("", byDate.body());

        HttpResponse<String> otherEtag = get("/catalog", "Accept", "application/json",
                "If-None-Match", "\"catalog-0\"");
        assertEquals(200, otherEtag.statusCode());
        assertEquals(full.body(), otherEtag.body());

        assertEquals(RENDERS_BEFORE_CACHED, storefront.renders("catalog.json"));
    }

    @Test
    void rendersAFreshCatalogOnceANewRevisionChangesItsValidators() throws Exception {
        HttpResponse<String> before = warmUp("/catalog");
        assertEquals(1, JSON.readTree(before.body()).get("revision").asInt());

        assertEquals(204, send("POST", "/admin/catalog", "", "Content-Type", "text/plain").statusCode());

        HttpResponse<String> after = get("/catalog", "Accept", "application/json");
        assertEquals(200, after.statusCode());
        assertEquals("\"catalog-2\"", after.headers().firstValue("ETag").orElseThrow());
        assertEquals("Sat, 02 Mar 2024 09:00:00 GMT", after.headers().firstValue("Last-Modified").orElseThrow());
        JsonNode catalog = JSON.readTree(after.body());
        assertEquals(2, catalog.get("revision").asInt());
        assertEquals(11.0, catalog.get("products").get(0).get("price").asDouble());
        assertEquals(RENDERS_BEFORE_CACHED + 1, storefront.renders("catalog.json"));
    }

    @Test
    void servesAStaleBannerUntilItsEntriesArePurged() throws Exception {
        assertEquals("Free shipping over 40 EUR", warmUp("/banner").body());

        assertEquals(204, send("POST", "/admin/banner", "Spring tea sale: 20% off", "Content-Type", "text/plain")
                .statusCode());
        assertEquals("Free shipping over 40 EUR", get("/banner").body());
        assertEquals(RENDERS_BEFORE_CACHED, storefront.renders("banner"));

        HttpResponse<String> purged = send("POST", "/admin/purge?path=/banner", "", "Content-Type", "text/plain");
        assertEquals(200, purged.statusCode());
        assertEquals("purged 1", purged.body());

        assertEquals("Spring tea sale: 20% off", get("/banner").body());
        assertEquals(RENDERS_BEFORE_CACHED + 1, storefront.renders("banner"));
    }

    @Test
    void neverStoresPostResponsesOrChunkedStreams() throws Exception {
        for (int i = 0; i < 8; i++) {
            HttpResponse<String> quote = send("POST", "/quote", "TEA-01 x 3", "Content-Type", "text/plain");
            assertEquals(200, quote.statusCode());
            assertEquals("TEA-01 x 3 = 37.50", quote.body());

            HttpResponse<String> ticker = get("/ticker");
            assertEquals(200, ticker.statusCode());
            assertEquals("TEA-01 12.50\nMUG-02 18.00\n", ticker.body());
            assertFalse(ticker.headers().firstValue("Content-Length").isPresent());
        }

        assertEquals(8, storefront.renders("quote"));
        assertEquals(8, storefront.renders("ticker"));
    }

    /// Requests a path until the cache serves it, and returns the last, cached response.
    private HttpResponse<String> warmUp(String path) throws IOException, InterruptedException {
        HttpResponse<String> response = null;
        for (int i = 0; i <= RENDERS_BEFORE_CACHED; i++) {
            response = get(path, "Accept", "application/json");
            assertEquals(200, response.statusCode());
        }
        return response;
    }

    private HttpResponse<String> get(String path, String... headers) throws IOException, InterruptedException {
        return send("GET", path, null, headers);
    }

    private HttpResponse<String> send(String method, String path, String body, String... headers)
            throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .method(method, publisher);
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /// The storefront's handlers, its published content, and a count of every render it performs.
    private static final class Storefront {

        private static final Instant FIRST_PUBLICATION = Instant.parse("2024-03-01T09:00:00Z");

        private final DirectBufferCache cache;
        private final AtomicInteger revision = new AtomicInteger(1);
        private final AtomicReference<String> banner = new AtomicReference<>("Free shipping over 40 EUR");
        private final Map<String, AtomicInteger> renders = new ConcurrentHashMap<>();

        Storefront(DirectBufferCache cache) {
            this.cache = cache;
        }

        int renders(String name) {
            AtomicInteger count = renders.get(name);
            return count == null ? 0 : count.get();
        }

        /// Sets the representation headers first: they are part of the cache key the lookup uses.
        void catalog(HttpServerExchange exchange) {
            String accept = exchange.getRequestHeaders().getFirst(Headers.ACCEPT);
            boolean csv = accept != null && accept.contains("text/csv");
            int current = revision.get();
            Instant published = FIRST_PUBLICATION.plus(Duration.ofDays(current - 1L));
            exchange.getResponseHeaders()
                    .put(Headers.CONTENT_TYPE, csv ? "text/csv" : "application/json")
                    .put(Headers.ETAG, "\"catalog-" + current + "\"")
                    .put(Headers.LAST_MODIFIED, DateUtils.toDateString(Date.from(published)));
            if (servedFromCache(exchange)) {
                return;
            }
            rendered(csv ? "catalog.csv" : "catalog.json");
            exchange.getResponseSender().send(csv ? catalogCsv(current) : catalogJson(current));
        }

        void banner(HttpServerExchange exchange) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            if (servedFromCache(exchange)) {
                return;
            }
            rendered("banner");
            exchange.getResponseSender().send(banner.get());
        }

        /// Streams the ticker in two writes, so the response is chunked and has no length.
        void ticker(HttpServerExchange exchange) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            if (servedFromCache(exchange)) {
                return;
            }
            rendered("ticker");
            exchange.getResponseSender().send("TEA-01 12.50\n", new IoCallback() {
                @Override
                public void onComplete(HttpServerExchange done, Sender sender) {
                    sender.send("MUG-02 18.00\n");
                }

                @Override
                public void onException(HttpServerExchange failed, Sender sender, IOException exception) {
                    IoCallback.END_EXCHANGE.onException(failed, sender, exception);
                }
            });
        }

        void quote(HttpServerExchange exchange) {
            exchange.getRequestReceiver().receiveFullString((received, line) -> {
                received.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                if (servedFromCache(received)) {
                    return;
                }
                rendered("quote");
                String[] parts = line.split(" x ");
                double unitPrice = parts[0].equals("TEA-01") ? 12.5 : 18.0;
                double total = unitPrice * Integer.parseInt(parts[1].trim());
                received.getResponseSender().send(line + " = " + String.format(Locale.ROOT, "%.2f", total));
            });
        }

        void publishCatalog(HttpServerExchange exchange) {
            revision.incrementAndGet();
            exchange.setStatusCode(204);
            exchange.endExchange();
        }

        void publishBanner(HttpServerExchange exchange) {
            exchange.getRequestReceiver().receiveFullString((received, text) -> {
                banner.set(text);
                received.setStatusCode(204);
                received.endExchange();
            });
        }

        /// Removes every cached variant of a path, whatever its content type or validators.
        void purge(HttpServerExchange exchange) {
            String path = exchange.getQueryParameters().get("path").getFirst();
            int purged = 0;
            for (Object key : cache.getAllKeys()) {
                if (key instanceof CachedHttpRequest request && request.getPath().equals(path)) {
                    cache.remove(key);
                    purged++;
                }
            }
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send("purged " + purged);
        }

        private static boolean servedFromCache(HttpServerExchange exchange) {
            return exchange.getAttachment(ResponseCache.ATTACHMENT_KEY).tryServeResponse();
        }

        private void rendered(String name) {
            renders.computeIfAbsent(name, ignored -> new AtomicInteger()).incrementAndGet();
        }

        private static String catalogJson(int revision) {
            ArrayNode products = JSON.createArrayNode();
            for (Product product : products(revision)) {
                products.addObject()
                        .put("sku", product.sku())
                        .put("name", product.name())
                        .put("price", product.price());
            }
            return JSON.createObjectNode()
                    .put("revision", revision)
                    .set("products", products)
                    .toString();
        }

        private static String catalogCsv(int revision) {
            StringBuilder csv = new StringBuilder("sku,name,price\n");
            for (Product product : products(revision)) {
                csv.append(product.sku()).append(',').append(product.name()).append(',')
                        .append(String.format(Locale.ROOT, "%.2f", product.price())).append('\n');
            }
            return csv.toString();
        }

        private static List<Product> products(int revision) {
            double teaPrice = revision == 1 ? 12.5 : 11.0;
            return List.of(new Product("TEA-01", "Sencha", teaPrice), new Product("MUG-02", "Stoneware mug", 18.0));
        }
    }

    private record Product(String sku, String name, double price) {
    }
}
