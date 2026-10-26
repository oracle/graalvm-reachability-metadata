/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.URLDecodingHandler;
import io.undertow.util.Headers;
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
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Per-endpoint URL character decoding with `URLDecodingHandler` supports a legacy document archive.
 *
 * Old desktop clients send Latin-1 percent-encoded document names and search parameters, while a
 * replacement portal sends UTF-8 to another prefix on the same listener. Both access the same
 * archive. Decoding happens before routing, with listener-level automatic decoding disabled.
 * Literal plus signs in document names remain distinct from spaces, query plus signs mean spaces,
 * and a percent escape in an actual document name must not be decoded twice.
 */
@Timeout(60)
public class LegacyArchiveUrlDecodingTest {

    private Undertow server;
    private HttpClient client;
    private URI base;

    @BeforeEach
    void startArchive() {
        Archive archive = new Archive();
        RoutingHandler routes = Handlers.routing()
                .get("/documents/{name}", archive::download)
                .get("/search", archive::search);
        server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(2)
                .setServerOption(UndertowOptions.DECODE_URL, false)
                .addHttpListener(0, "127.0.0.1")
                .setHandler(Handlers.path()
                        .addPrefixPath("/legacy", new URLDecodingHandler(routes,
                                StandardCharsets.ISO_8859_1.name()))
                        .addPrefixPath("/portal", new URLDecodingHandler(routes,
                                StandardCharsets.UTF_8.name())))
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        base = URI.create("http://127.0.0.1:" + address.getPort());
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterEach
    void stopArchive() {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    void legacyAndModernClientsRetrieveAndSearchTheSameDocuments() throws Exception {
        assertDocument("/legacy/documents/caf%E9+menu.txt", "Beverages: espresso, tea\n");
        assertDocument("/legacy/documents/caf%E9%2Bmenu.txt", "Beverages: espresso, tea\n");
        assertDocument("/legacy/documents/caf%E9%20menu.txt", "Lunch: soup, salad\n");
        assertDocument("/portal/documents/caf%C3%A9%2Bmenu.txt", "Beverages: espresso, tea\n");
        assertDocument("/portal/documents/caf%C3%A9%20menu.txt", "Lunch: soup, salad\n");
        assertDocument("/legacy/documents/tax%2520notes.txt", "VAT retained at 20 percent\n");

        HttpResponse<String> missing = get("/legacy/documents/tax%20notes.txt");
        assertEquals(404, missing.statusCode());
        assertEquals("Document not found\n", missing.body());

        String expectedMatch = "caf\u00e9+menu.txt\n";
        assertDocument("/legacy/search?cat%E9gorie=boissons+chaudes&tag=%E9t%E9&tag=C%2B%2B",
                expectedMatch);
        assertDocument("/portal/search?cat%C3%A9gorie=boissons%20chaudes&tag=%C3%A9t%C3%A9&tag=C%2B%2B",
                expectedMatch);
        assertDocument("/legacy/search?cat%E9gorie=boissons+chaudes&tag=%E9t%E9",
                "caf\u00e9+menu.txt\ncaf\u00e9 menu.txt\n");
    }

    private void assertDocument(String path, String expected) throws IOException, InterruptedException {
        HttpResponse<String> response = get(path);
        assertEquals(200, response.statusCode(), path);
        assertEquals("text/plain; charset=UTF-8",
                response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(expected, response.body(), path);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void text(HttpServerExchange exchange, String body) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
        exchange.getResponseSender().send(body, StandardCharsets.UTF_8);
    }

    private record Document(String name, String category, List<String> tags, String body) {
    }

    private static final class Archive {

        private final List<Document> documents = List.of(
                new Document("caf\u00e9+menu.txt", "boissons chaudes", List.of("\u00e9t\u00e9", "C++"),
                        "Beverages: espresso, tea\n"),
                new Document("caf\u00e9 menu.txt", "boissons chaudes", List.of("\u00e9t\u00e9"),
                        "Lunch: soup, salad\n"),
                new Document("tax%20notes.txt", "accounting", List.of("tax"),
                        "VAT retained at 20 percent\n"));

        private void download(HttpServerExchange exchange) {
            String name = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("name");
            for (Document document : documents) {
                if (document.name().equals(name)) {
                    text(exchange, document.body());
                    return;
                }
            }
            exchange.setStatusCode(StatusCodes.NOT_FOUND);
            text(exchange, "Document not found\n");
        }

        private void search(HttpServerExchange exchange) {
            Deque<String> categories = exchange.getQueryParameters().get("cat\u00e9gorie");
            Deque<String> tags = exchange.getQueryParameters().get("tag");
            if (categories == null || categories.size() != 1) {
                exchange.setStatusCode(StatusCodes.BAD_REQUEST);
                text(exchange, "One category is required\n");
                return;
            }
            StringBuilder matches = new StringBuilder();
            for (Document document : documents) {
                if (document.category().equals(categories.getFirst())
                        && (tags == null || document.tags().containsAll(tags))) {
                    matches.append(document.name()).append('\n');
                }
            }
            text(exchange, matches.toString());
        }
    }
}
