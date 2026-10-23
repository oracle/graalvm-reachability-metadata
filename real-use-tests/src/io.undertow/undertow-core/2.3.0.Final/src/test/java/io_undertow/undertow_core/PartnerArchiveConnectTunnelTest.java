/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ConnectHandler;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * HTTP CONNECT tunneling with `ConnectHandler` provides restricted access to a partner archive.
 *
 * An egress gateway permits only the configured partner authority, rather than acting as an open
 * proxy. A client establishes a tunnel, uploads a binary document, then retrieves it on the same
 * connection. Plain HTTP inside the tunnel keeps this scenario focused on transparent TCP
 * forwarding; in deployment the client would normally negotiate TLS after CONNECT succeeds.
 */
@Timeout(60)
public class PartnerArchiveConnectTunnelTest {

    private Undertow archive;
    private Undertow gateway;
    private InetSocketAddress gatewayAddress;
    private String partnerAuthority;

    @BeforeEach
    void startPartnerAndEgressGateway() {
        Archive documents = new Archive();
        archive = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(2)
                .addHttpListener(0, "127.0.0.1")
                .setHandler(Handlers.routing()
                        .post("/documents", documents::store)
                        .get("/documents/latest", documents::download))
                .build();
        archive.start();
        InetSocketAddress partnerAddress = (InetSocketAddress) archive.getListenerInfo().get(0).getAddress();
        partnerAuthority = "127.0.0.1:" + partnerAddress.getPort();

        ConnectHandler tunnels = new ConnectHandler(
                exchange -> exchange.getResponseSender().send("Egress gateway ready"),
                exchange -> partnerAuthority.equals(exchange.getRequestPath()));
        gateway = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(2)
                .addHttpListener(0, "127.0.0.1")
                .setHandler(tunnels)
                .build();
        gateway.start();
        gatewayAddress = (InetSocketAddress) gateway.getListenerInfo().get(0).getAddress();
    }

    @AfterEach
    void stopGatewayAndPartner() {
        try {
            if (gateway != null) {
                gateway.stop();
            }
        } finally {
            if (archive != null) {
                archive.stop();
            }
        }
    }

    @Test
    void uploadsAndRetrievesABinaryDocumentThroughOnePersistentTunnel() throws Exception {
        byte[] document = new byte[65536];
        for (int i = 0; i < document.length; i++) {
            document[i] = (byte) (i * 31);
        }

        try (Socket socket = connectToGateway()) {
            InputStream input = new BufferedInputStream(socket.getInputStream());
            write(socket, """
                    CONNECT %s HTTP/1.1
                    Host: %s

                    """.formatted(partnerAuthority, partnerAuthority));
            assertEquals(200, ResponseHead.read(input).status());

            write(socket, """
                    POST /documents HTTP/1.1
                    Host: %s
                    Content-Type: application/octet-stream
                    Content-Length: %d

                    """.formatted(partnerAuthority, document.length));
            socket.getOutputStream().write(document);
            socket.getOutputStream().flush();
            ResponseHead stored = ResponseHead.read(input);
            assertEquals(201, stored.status());
            assertEquals("/documents/latest", stored.headers().get("Location"));
            assertEquals("Archived 65536 bytes", new String(stored.body(input), StandardCharsets.UTF_8));

            write(socket, """
                    GET /documents/latest HTTP/1.1
                    Host: %s

                    """.formatted(partnerAuthority));
            ResponseHead downloaded = ResponseHead.read(input);
            assertEquals(200, downloaded.status());
            assertEquals("application/octet-stream", downloaded.headers().get("Content-Type"));
            assertArrayEquals(document, downloaded.body(input));
        }
    }

    @Test
    void deniesOtherDestinationsWithoutBreakingTheGatewayHttpEndpoint() throws Exception {
        // Both the host spelling and port must match the explicit partner allow-list.
        String[] forbidden = {"localhost:" + partnerAuthority.split(":")[1], "127.0.0.1:0"};
        for (String authority : forbidden) {
            try (Socket socket = connectToGateway()) {
                write(socket, """
                        CONNECT %s HTTP/1.1
                        Host: %s

                        """.formatted(authority, authority));
                assertEquals(405, ResponseHead.read(socket.getInputStream()).status());
            }
        }

        try (Socket socket = connectToGateway()) {
            InputStream input = new BufferedInputStream(socket.getInputStream());
            write(socket, """
                    GET /health HTTP/1.1
                    Host: gateway.example

                    """);
            ResponseHead health = ResponseHead.read(input);
            assertEquals(200, health.status());
            assertEquals("Egress gateway ready", new String(health.body(input), StandardCharsets.UTF_8));
        }
    }

    private Socket connectToGateway() throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(gatewayAddress, 10000);
            socket.setSoTimeout(10000);
            return socket;
        } catch (IOException failure) {
            socket.close();
            throw failure;
        }
    }

    private static void write(Socket socket, String request) throws IOException {
        socket.getOutputStream().write(request.replace("\n", "\r\n").getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private static final class Archive {

        private final AtomicReference<byte[]> latest = new AtomicReference<>();

        private void store(HttpServerExchange exchange) {
            exchange.getRequestReceiver().receiveFullBytes((request, document) -> {
                latest.set(document);
                request.setStatusCode(StatusCodes.CREATED);
                request.getResponseHeaders().put(Headers.LOCATION, "/documents/latest");
                request.getResponseSender().send("Archived " + document.length + " bytes");
            });
        }

        private void download(HttpServerExchange exchange) {
            byte[] document = latest.get();
            if (document == null) {
                exchange.setStatusCode(StatusCodes.NOT_FOUND);
                exchange.endExchange();
                return;
            }
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/octet-stream");
            exchange.setResponseContentLength(document.length);
            exchange.getResponseSender().send(ByteBuffer.wrap(document));
        }
    }

    private record ResponseHead(int status, Map<String, String> headers) {

        private static ResponseHead read(InputStream input) throws IOException {
            int status = Integer.parseInt(line(input).split(" ", 3)[1]);
            Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (int i = 0; i < 64; i++) {
                String header = line(input);
                if (header.isEmpty()) {
                    return new ResponseHead(status, headers);
                }
                int colon = header.indexOf(':');
                headers.put(header.substring(0, colon), header.substring(colon + 1).trim());
            }
            throw new IOException("Too many HTTP response headers");
        }

        private byte[] body(InputStream input) throws IOException {
            int length = Integer.parseInt(headers.get("Content-Length"));
            byte[] body = input.readNBytes(length);
            assertEquals(length, body.length, "The tunnel must deliver the complete response");
            return body;
        }

        private static String line(InputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (int i = 0; i < 8192; i++) {
                int next = input.read();
                if (next == -1) {
                    throw new EOFException("Connection closed before the response headers were complete");
                }
                if (next == '\n') {
                    return bytes.toString(StandardCharsets.US_ASCII).stripTrailing();
                }
                bytes.write(next);
            }
            throw new IOException("HTTP response header line too long");
        }
    }
}
