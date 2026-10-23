/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.undertow.Undertow;
import io.undertow.server.handlers.ChannelUpgradeHandler;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.xnio.StreamConnection;
import org.xnio.streams.ChannelInputStream;
import org.xnio.streams.ChannelOutputStream;

/**
 * HTTP protocol upgrades with `ChannelUpgradeHandler` open a binary shipping-label spool connection.
 *
 * A local warehouse agent discovers the spool service over HTTP, then negotiates the label-spool/1
 * protocol on the same listener. Length-prefixed jobs are persisted before acknowledgment, and
 * multiple jobs share one upgraded connection. Unknown protocol versions and the wrong endpoint
 * stay HTTP requests instead of opening a spool connection.
 */
@Timeout(60)
public class ShippingLabelProtocolUpgradeTest {

    @TempDir
    Path spool;

    private Undertow server;
    private InetSocketAddress address;
    private final CompletableFuture<Void> spoolFinished = new CompletableFuture<>();

    @BeforeEach
    void startWarehouseSpoolService() {
        ChannelUpgradeHandler upgrades = new ChannelUpgradeHandler();
        upgrades.addProtocol(
                "label-spool/1",
                (connection, exchange) -> connection.getWorker().execute(() -> receiveJobs(connection)),
                exchange -> "/spool/labels".equals(exchange.getRequestPath()));
        upgrades.setNonUpgradeHandler(exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            if ("/health".equals(exchange.getRequestPath())) {
                exchange.getResponseSender().send("Label spool ready");
            } else if ("/spool/labels".equals(exchange.getRequestPath())) {
                exchange.setStatusCode(StatusCodes.UPGRADE_REQUIRED);
                exchange.getResponseSender().send("Use label-spool/1");
            } else {
                exchange.setStatusCode(StatusCodes.NOT_FOUND);
                exchange.getResponseSender().send("Unknown spool endpoint");
            }
        });
        server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(2)
                .addHttpListener(0, "127.0.0.1")
                .setHandler(upgrades)
                .build();
        server.start();
        address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
    }

    @AfterEach
    void stopSpoolService() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void negotiatesAndPersistsTwoBinaryLabelsOnOneUpgradedConnection() throws Exception {
        byte[] firstLabel = new byte[32768];
        for (int i = 0; i < firstLabel.length; i++) {
            firstLabel[i] = (byte) (i * 17);
        }
        byte[] secondLabel = "Shipment 1043\nDock B\n".getBytes(StandardCharsets.UTF_8);

        try (Socket socket = connect()) {
            InputStream input = new BufferedInputStream(socket.getInputStream());
            writeHttp(socket, """
                    GET /spool/labels HTTP/1.1
                    Host: warehouse.example
                    Connection: Upgrade
                    Upgrade: label-spool/1

                    """);
            ResponseHead switching = ResponseHead.read(input);
            assertEquals(101, switching.status());
            assertEquals("upgrade", switching.headers().get("Connection").toLowerCase(Locale.ROOT));
            assertEquals("label-spool/1", switching.headers().get("Upgrade"));

            DataOutputStream jobs = new DataOutputStream(socket.getOutputStream());
            DataInputStream receipts = new DataInputStream(input);
            submit(jobs, receipts, "shipment-1042", firstLabel);
            submit(jobs, receipts, "shipment-1043", secondLabel);
            jobs.writeUTF("QUIT");
            jobs.flush();
            assertEquals("Spool complete", receipts.readUTF());
            assertEquals(-1, input.read(), "The application protocol closes after the final receipt");
        }
        spoolFinished.get(10, TimeUnit.SECONDS);
        assertArrayEquals(firstLabel, Files.readAllBytes(spool.resolve("shipment-1042")));
        assertArrayEquals(secondLabel, Files.readAllBytes(spool.resolve("shipment-1043")));
    }

    @Test
    void keepsDiscoveryAndUnsupportedNegotiationsInHttp() throws Exception {
        try (Socket socket = connect()) {
            writeHttp(socket, """
                    GET /health HTTP/1.1
                    Host: warehouse.example
                    Connection: close

                    """);
            InputStream input = socket.getInputStream();
            ResponseHead health = ResponseHead.read(input);
            assertEquals(200, health.status());
            assertEquals("Label spool ready", health.body(input));
        }
        try (Socket socket = connect()) {
            writeHttp(socket, """
                    GET /spool/labels HTTP/1.1
                    Host: warehouse.example
                    Connection: Upgrade, close
                    Upgrade: label-spool/2

                    """);
            InputStream input = socket.getInputStream();
            ResponseHead unsupported = ResponseHead.read(input);
            assertEquals(426, unsupported.status());
            assertEquals("Use label-spool/1", unsupported.body(input));
        }
        try (Socket socket = connect()) {
            writeHttp(socket, """
                    GET /spool/invoices HTTP/1.1
                    Host: warehouse.example
                    Connection: Upgrade, close
                    Upgrade: label-spool/1

                    """);
            InputStream input = socket.getInputStream();
            ResponseHead wrongEndpoint = ResponseHead.read(input);
            assertEquals(404, wrongEndpoint.status());
            assertEquals("Unknown spool endpoint", wrongEndpoint.body(input));
        }
        assertFalse(spoolFinished.isDone(), "Rejected upgrades must not run the job receiver");
        try (Stream<Path> files = Files.list(spool)) {
            assertEquals(0, files.count());
        }
    }

    private void receiveJobs(StreamConnection connection) {
        try (connection) {
            DataInputStream jobs = new DataInputStream(
                    new ChannelInputStream(connection.getSourceChannel(), 10, TimeUnit.SECONDS));
            DataOutputStream receipts = new DataOutputStream(
                    new ChannelOutputStream(connection.getSinkChannel(), 10, TimeUnit.SECONDS));
            while (true) {
                String job = jobs.readUTF();
                if ("QUIT".equals(job)) {
                    receipts.writeUTF("Spool complete");
                    receipts.flush();
                    break;
                }
                int length = jobs.readInt();
                if (!job.matches("shipment-[0-9]+") || length < 0 || length > 65536) {
                    throw new IOException("Invalid label job");
                }
                byte[] label = new byte[length];
                jobs.readFully(label);
                Files.write(spool.resolve(job), label);
                receipts.writeUTF("Stored " + job);
                receipts.writeInt(length);
                receipts.flush();
            }
        } catch (IOException failure) {
            spoolFinished.completeExceptionally(failure);
            return;
        }
        spoolFinished.complete(null);
    }

    private static void submit(DataOutputStream jobs, DataInputStream receipts, String id, byte[] label)
            throws IOException {
        jobs.writeUTF(id);
        jobs.writeInt(label.length);
        jobs.write(label);
        jobs.flush();
        assertEquals("Stored " + id, receipts.readUTF());
        assertEquals(label.length, receipts.readInt());
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(address, 10000);
            socket.setSoTimeout(10000);
            return socket;
        } catch (IOException failure) {
            socket.close();
            throw failure;
        }
    }

    private static void writeHttp(Socket socket, String request) throws IOException {
        socket.getOutputStream().write(request.replace("\n", "\r\n").getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
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

        private String body(InputStream input) throws IOException {
            int length = Integer.parseInt(headers.get("Content-Length"));
            byte[] body = input.readNBytes(length);
            assertEquals(length, body.length);
            return new String(body, StandardCharsets.UTF_8);
        }

        private static String line(InputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (int i = 0; i < 8192; i++) {
                int next = input.read();
                if (next == -1) {
                    throw new EOFException("Connection closed in HTTP response headers");
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
