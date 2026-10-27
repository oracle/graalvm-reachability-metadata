/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.Undertow;
import io.undertow.Undertow.ListenerBuilder;
import io.undertow.Undertow.ListenerType;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * PROXY-protocol listeners preserve client connection identities for an audit-ingestion service.
 *
 * A private listener accepts traffic only from a trusted Layer-4 load balancer, which prefixes
 * each connection with a text v1 or binary v2 PROXY header. The application persists inventory
 * events with the original client and service addresses, not the balancer's loopback address.
 * HTTP forwarding headers are untrusted and must not replace that connection identity. The
 * socket client represents the balancer's wire protocol; no external load balancer is required.
 */
@Timeout(60)
public class AuditIngressProxyProtocolTest {

    @TempDir
    Path directory;

    private Undertow server;
    private InetSocketAddress listener;
    private Path auditLog;

    @BeforeEach
    void startPrivateAuditListener() {
        auditLog = directory.resolve("inventory-audit.tsv");
        server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(2)
                .addListener(new ListenerBuilder()
                        .setType(ListenerType.HTTP)
                        .setHost("127.0.0.1")
                        .setPort(0)
                        .setUseProxyProtocol(true))
                .setHandler(new BlockingHandler(this::recordEvent))
                .build();
        server.start();
        listener = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
    }

    @AfterEach
    void stopAuditListener() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void recordsOriginalIpv4AndIpv6ClientsAcrossBothProxyProtocolVersions() throws Exception {
        byte[] ipv4Preamble = "PROXY TCP4 192.0.2.41 198.51.100.20 41001 8443\r\n"
                .getBytes(StandardCharsets.US_ASCII);
        submit(ipv4Preamble, "reserve sku-41 quantity=2");
        String first = row("reserve sku-41 quantity=2", "192.0.2.41", 41001, "198.51.100.20", 8443);
        assertEquals(List.of(first), Files.readAllLines(auditLog));

        byte[] ipv6Preamble = "PROXY TCP6 2001:db8::41 2001:db8::20 41002 9443\r\n"
                .getBytes(StandardCharsets.US_ASCII);
        submit(ipv6Preamble, "release sku-41 quantity=1");
        String second = row("release sku-41 quantity=1", "2001:db8::41", 41002, "2001:db8::20", 9443);
        assertEquals(List.of(first, second), Files.readAllLines(auditLog));

        submit(binaryPreamble("192.0.2.99", "198.51.100.21", 42001, 8443),
                "receive sku-99 quantity=8");
        String third = row("receive sku-99 quantity=8", "192.0.2.99", 42001, "198.51.100.21", 8443);
        assertEquals(List.of(first, second, third), Files.readAllLines(auditLog));
    }

    private void recordEvent(HttpServerExchange exchange) throws IOException {
        if (!exchange.getRequestPath().equals("/audit")
                || !exchange.getRequestMethod().equalToString("POST")) {
            exchange.setStatusCode(StatusCodes.NOT_FOUND);
            exchange.endExchange();
            return;
        }
        exchange.startBlocking();
        String event = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!event.matches("(reserve|release|receive) sku-[0-9]+ quantity=[0-9]+")) {
            exchange.setStatusCode(StatusCodes.BAD_REQUEST);
            exchange.endExchange();
            return;
        }
        InetSocketAddress client = exchange.getSourceAddress();
        InetSocketAddress service = exchange.getDestinationAddress();
        String entry = event + "\t" + client.getAddress().getHostAddress() + "\t" + client.getPort()
                + "\t" + service.getAddress().getHostAddress() + "\t" + service.getPort() + "\n";
        Files.writeString(auditLog, entry, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        byte[] receipt = "Audit recorded".getBytes(StandardCharsets.UTF_8);
        exchange.setStatusCode(StatusCodes.CREATED);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
        exchange.setResponseContentLength(receipt.length);
        exchange.getOutputStream().write(receipt);
    }

    private void submit(byte[] preamble, String event) throws IOException {
        try (Socket connection = new Socket()) {
            connection.connect(listener, 10000);
            connection.setSoTimeout(10000);
            byte[] body = event.getBytes(StandardCharsets.UTF_8);
            String request = """
                    POST /audit HTTP/1.1
                    Host: audit.internal
                    Content-Type: text/plain; charset=UTF-8
                    Content-Length: %d
                    Forwarded: for=203.0.113.250;host=untrusted.example
                    X-Forwarded-For: 203.0.113.251
                    Connection: close

                    """.formatted(body.length).replace("\n", "\r\n");
            connection.getOutputStream().write(preamble);
            connection.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            connection.getOutputStream().write(body);
            connection.getOutputStream().flush();

            String response = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(response.startsWith("HTTP/1.1 201 "), response);
            int headerEnd = response.indexOf("\r\n\r\n");
            assertTrue(headerEnd >= 0, response);
            assertEquals("Audit recorded", response.substring(headerEnd + 4));
        }
    }

    private static String row(String event, String client, int clientPort, String service, int servicePort)
            throws IOException {
        return event + "\t" + InetAddress.getByName(client).getHostAddress() + "\t" + clientPort
                + "\t" + InetAddress.getByName(service).getHostAddress() + "\t" + servicePort;
    }

    private static byte[] binaryPreamble(String client, String service, int clientPort, int servicePort)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream header = new DataOutputStream(bytes)) {
            // PROXY v2 signature, version 2 / PROXY command, IPv4 / STREAM, 12 address bytes.
            header.write(new byte[] {13, 10, 13, 10, 0, 13, 10, 81, 85, 73, 84, 10});
            header.writeByte(0x21);
            header.writeByte(0x11);
            header.writeShort(12);
            header.write(InetAddress.getByName(client).getAddress());
            header.write(InetAddress.getByName(service).getAddress());
            header.writeShort(clientPort);
            header.writeShort(servicePort);
        }
        return bytes.toByteArray();
    }
}
