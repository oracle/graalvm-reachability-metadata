/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.NameVirtualHostHandler;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Name-based virtual hosting with `NameVirtualHostHandler` routes tenant portals on one listener.
 *
 * Two companies publish notices at the same path on different domains. A company's canonical
 * domain and its www alias share notices, while the other company's portal stays independent.
 * Unknown domains reach a neutral fallback rather than leaking a tenant's page. The HTTP client
 * connects directly to loopback and supplies the requested domain without depending on DNS.
 */
@Timeout(60)
public class TenantPortalVirtualHostTest {

    private Undertow server;
    private InetSocketAddress address;

    @BeforeEach
    void startPortals() {
        TenantPortal orchard = new TenantPortal("Orchard", "Harvest market on Saturday");
        TenantPortal harbor = new TenantPortal("Harbor", "Ferry terminal open");
        NameVirtualHostHandler hosts = new NameVirtualHostHandler()
                .addHost("orchard.example", orchard)
                .addHost("www.orchard.example", orchard)
                .addHost("harbor.example", harbor)
                .setDefaultHandler(exchange -> text(exchange, StatusCodes.NOT_FOUND, "No portal for this domain"));
        server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setIoThreads(1)
                .setWorkerThreads(2)
                .setHandler(hosts)
                .build();
        server.start();
        address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
    }

    @AfterEach
    void stopPortals() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void updatesANoticeAcrossAliasesWithoutChangingAnotherTenantsPortal() throws IOException {
        assertReply(200, "Orchard: Harvest market on Saturday", request("orchard.example", "GET", ""));
        assertReply(200, "Harbor: Ferry terminal open", request("harbor.example", "GET", ""));

        assertReply(204, "", request("orchard.example", "PUT", "Market moved to Sunday"));
        assertReply(200, "Orchard: Market moved to Sunday", request("www.orchard.example", "GET", ""));
        assertReply(200, "Orchard: Market moved to Sunday", request("orchard.example", "GET", ""));
        assertReply(200, "Harbor: Ferry terminal open", request("harbor.example", "GET", ""));
    }

    @Test
    void matchesDomainsCaseInsensitivelyWithPortsAndRejectsUnregisteredDomains() throws IOException {
        assertReply(200, "Orchard: Harvest market on Saturday",
                request("ORCHARD.EXAMPLE:" + address.getPort(), "GET", ""));
        assertReply(200, "Harbor: Ferry terminal open",
                request("harbor.example:" + address.getPort(), "GET", ""));

        assertReply(404, "No portal for this domain", request("unregistered.example", "GET", ""));
        assertReply(404, "No portal for this domain", request("orchard.example.attacker.example", "GET", ""));
        assertReply(404, "No portal for this domain", request("127.0.0.1", "PUT", "Unwanted notice"));
        assertReply(200, "Orchard: Harvest market on Saturday", request("orchard.example", "GET", ""));
        assertReply(200, "Harbor: Ferry terminal open", request("harbor.example", "GET", ""));
    }

    /// Sends a close-delimited HTTP/1.1 exchange with an explicit virtual-host name.
    private Reply request(String host, String method, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String headers = method + " /notice HTTP/1.1\r\n"
                + "Host: " + host + "\r\n"
                + "Connection: close\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Length: " + payload.length + "\r\n\r\n";
        try (Socket socket = new Socket()) {
            socket.connect(address, 10_000);
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(payload);
            socket.getOutputStream().flush();
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int boundary = response.indexOf("\r\n\r\n");
            if (boundary < 0) {
                throw new IOException("Incomplete HTTP response: " + response);
            }
            int status = Integer.parseInt(response.substring(0, response.indexOf("\r\n")).split(" ")[1]);
            return new Reply(status, response.substring(boundary + 4));
        }
    }

    private static void assertReply(int status, String body, Reply reply) {
        assertEquals(status, reply.status());
        assertEquals(body, reply.body());
    }

    private static void text(HttpServerExchange exchange, int status, String body) {
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
        exchange.setResponseContentLength(body.getBytes(StandardCharsets.UTF_8).length);
        exchange.getResponseSender().send(body, StandardCharsets.UTF_8);
    }

    private record Reply(int status, String body) {
    }

    /// Each company's application instance owns its notice independently of its domain aliases.
    private static final class TenantPortal implements HttpHandler {

        private final String company;
        private final AtomicReference<String> notice;

        private TenantPortal(String company, String initialNotice) {
            this.company = company;
            notice = new AtomicReference<>(initialNotice);
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) {
            if (!exchange.getRequestPath().equals("/notice")) {
                text(exchange, StatusCodes.NOT_FOUND, "Unknown page");
            } else if (Methods.GET.equals(exchange.getRequestMethod())) {
                text(exchange, StatusCodes.OK, company + ": " + notice.get());
            } else if (Methods.PUT.equals(exchange.getRequestMethod())) {
                exchange.getRequestReceiver().receiveFullString((ready, body) -> {
                    notice.set(body);
                    ready.setStatusCode(StatusCodes.NO_CONTENT);
                    ready.endExchange();
                }, StandardCharsets.UTF_8);
            } else {
                exchange.getResponseHeaders().put(Headers.ALLOW, "GET, PUT");
                text(exchange, StatusCodes.METHOD_NOT_ALLOWED, "Method not allowed");
            }
        }
    }
}
