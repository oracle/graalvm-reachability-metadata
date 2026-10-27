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
import io.undertow.server.handlers.CanonicalPathHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Canonical request-path normalization with `CanonicalPathHandler` protects workspace document routing.
 *
 * A workspace publishes a public handbook and a restricted budget snapshot. Desktop clients build
 * links with dot segments and duplicate separators. Normalization runs before prefix routing so
 * equivalent document paths share an access policy: a URL beginning with the public prefix cannot
 * use a parent segment to reach a private document without the workspace's download ticket.
 * This is URL routing, not a filesystem traversal filter; document bodies come from a snapshot map.
 */
@Timeout(60)
public class WorkspaceCanonicalPathTest {

    private static final HttpString WORKSPACE_AREA = new HttpString("X-Workspace-Area");
    private static final String DOWNLOAD_TICKET = "Bearer workspace-budget-download";
    private static final String HANDBOOK = "Welcome to the shared workspace.\n";
    private static final String BUDGET = "department,limit\nengineering,42000\n";

    private Undertow server;
    private HttpClient client;
    private String base;

    @BeforeEach
    void startWorkspace() {
        DocumentWorkspace workspace = new DocumentWorkspace();
        server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(2)
                .addHttpListener(0, "127.0.0.1")
                .setHandler(new CanonicalPathHandler(Handlers.path()
                        .addPrefixPath("/public", workspace::publicDocument)
                        .addPrefixPath("/private", workspace::privateDocument)))
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        base = "http://127.0.0.1:" + address.getPort();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterEach
    void stopWorkspace() {
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
    void normalizesDocumentLinksBeforeSelectingTheirAccessPolicy() throws Exception {
        for (String path : new String[] {
                "/public/handbook.txt",
                "/public/./handbook.txt",
                "/public/team/../handbook.txt",
                "//public///handbook.txt",
                "/private/../public/handbook.txt"
        }) {
            HttpResponse<String> response = get(path, false);
            assertEquals(200, response.statusCode(), path);
            assertEquals(HANDBOOK, response.body(), path);
            assertEquals("public", response.headers().firstValue("X-Workspace-Area").orElseThrow());
        }

        for (String path : new String[] {
                "/private/budget.csv",
                "/public/../private/budget.csv",
                "/public/team/../../private/./budget.csv",
                "/public/../private/finance/../budget.csv"
        }) {
            HttpResponse<String> denied = get(path, false);
            assertEquals(403, denied.statusCode(), path);
            assertEquals("A budget download ticket is required", denied.body(), path);
            assertEquals("private", denied.headers().firstValue("X-Workspace-Area").orElseThrow());

            HttpResponse<String> permitted = get(path, true);
            assertEquals(200, permitted.statusCode(), path);
            assertEquals(BUDGET, permitted.body(), path);
            assertEquals("private", permitted.headers().firstValue("X-Workspace-Area").orElseThrow());
        }

        HttpResponse<String> missing = get("/public/drafts/../missing.txt", false);
        assertEquals(404, missing.statusCode());
        assertEquals("Document not found", missing.body());
    }

    private HttpResponse<String> get(String path, boolean withTicket)
            throws IOException, InterruptedException {
        // Do not use URI.resolve or normalize: send the desktop client's original path verbatim.
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(10))
                .GET();
        if (withTicket) {
            request.header("Authorization", DOWNLOAD_TICKET);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static final class DocumentWorkspace {

        private final Map<String, String> publicDocuments = Map.of("/handbook.txt", HANDBOOK);
        private final Map<String, String> privateDocuments = Map.of("/budget.csv", BUDGET);

        private void publicDocument(HttpServerExchange exchange) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
            exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "no-store");
            exchange.getResponseHeaders().put(WORKSPACE_AREA, "public");
            sendDocument(exchange, publicDocuments);
        }

        private void privateDocument(HttpServerExchange exchange) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
            exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "no-store");
            exchange.getResponseHeaders().put(WORKSPACE_AREA, "private");
            if (!DOWNLOAD_TICKET.equals(exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION))) {
                exchange.setStatusCode(StatusCodes.FORBIDDEN);
                exchange.getResponseSender().send("A budget download ticket is required");
                return;
            }
            sendDocument(exchange, privateDocuments);
        }

        private void sendDocument(HttpServerExchange exchange, Map<String, String> documents) {
            if (!Methods.GET.equals(exchange.getRequestMethod())) {
                exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
                exchange.endExchange();
                return;
            }
            String document = documents.get(exchange.getRelativePath());
            if (document == null) {
                exchange.setStatusCode(StatusCodes.NOT_FOUND);
                exchange.getResponseSender().send("Document not found");
                return;
            }
            exchange.getResponseSender().send(document);
        }
    }
}
