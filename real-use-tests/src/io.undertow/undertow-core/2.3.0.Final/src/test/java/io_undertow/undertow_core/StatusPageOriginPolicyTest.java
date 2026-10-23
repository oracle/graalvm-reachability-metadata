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
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.OriginHandler;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Browser-origin enforcement with `OriginHandler` protects status-page announcement updates.
 *
 * An operator publishes announcements from either of two trusted HTTPS consoles. The public
 * status page is readable without an Origin header, but writes require a trusted origin. This
 * browser-origin check is an additional defense against cross-site writes, not authentication
 * or a CORS implementation. Rejected requests must never replace the published announcement.
 */
@Timeout(60)
public class StatusPageOriginPolicyTest {

    private static final String PRIMARY_CONSOLE = "https://console.status.example";
    private static final String BACKUP_CONSOLE = "https://recovery.status.example";

    private Undertow server;
    private HttpClient client;
    private URI announcement;

    @BeforeEach
    void startStatusPage() {
        StatusPage page = new StatusPage();
        OriginHandler originPolicy = new OriginHandler()
                .addAllowedOrigins(PRIMARY_CONSOLE, BACKUP_CONSOLE)
                .setRequireOriginHeader(true)
                .setRequireAllOrigins(true)
                .setOriginFailedHandler(exchange -> {
                    exchange.setStatusCode(StatusCodes.FORBIDDEN);
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
                    exchange.getResponseSender().send("Publish announcements from a trusted console");
                })
                .setNext(page::publish);
        server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(2)
                .addHttpListener(0, "127.0.0.1")
                .setHandler(Handlers.routing()
                        .get("/announcement", page::read)
                        .post("/announcement", originPolicy))
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        announcement = URI.create("http://127.0.0.1:" + address.getPort() + "/announcement");
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterEach
    void stopStatusPage() {
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
    void onlyTrustedConsolesCanReplaceThePublicAnnouncement() throws Exception {
        assertAnnouncement("All systems operational");

        HttpResponse<String> published = publish("Maintenance begins at 22:00 UTC", PRIMARY_CONSOLE);
        assertEquals(200, published.statusCode());
        assertEquals("Maintenance begins at 22:00 UTC", published.body());
        assertAnnouncement("Maintenance begins at 22:00 UTC");

        assertRejected(publish("Untrusted replacement", "https://untrusted.example"));
        assertRejected(publish("Missing origin replacement"));
        assertRejected(publish("Opaque origin replacement", "null"));
        assertRejected(publish("Insecure console replacement", "http://console.status.example"));
        assertRejected(publish("Lookalike replacement", "https://console.status.example.untrusted.example"));
        assertRejected(publish("Mixed origins replacement", PRIMARY_CONSOLE, "https://untrusted.example"));

        HttpResponse<String> recovered = publish("Maintenance complete", BACKUP_CONSOLE);
        assertEquals(200, recovered.statusCode());
        assertEquals("Maintenance complete", recovered.body());
        assertAnnouncement("Maintenance complete");
    }

    private HttpResponse<String> publish(String message, String... origins)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(announcement)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "text/plain; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(message));
        for (String origin : origins) {
            request.header("Origin", origin);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertRejected(HttpResponse<String> response) throws IOException, InterruptedException {
        assertEquals(403, response.statusCode());
        assertEquals("Publish announcements from a trusted console", response.body());
        assertAnnouncement("Maintenance begins at 22:00 UTC");
    }

    private void assertAnnouncement(String expected) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(announcement)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("text/plain; charset=UTF-8",
                response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(expected, response.body());
    }

    private static final class StatusPage {

        private final AtomicReference<String> announcement = new AtomicReference<>("All systems operational");

        private void read(HttpServerExchange exchange) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
            exchange.getResponseSender().send(announcement.get());
        }

        private void publish(HttpServerExchange exchange) {
            exchange.getRequestReceiver().receiveFullString((request, message) -> {
                announcement.set(message);
                read(request);
            });
        }
    }
}
