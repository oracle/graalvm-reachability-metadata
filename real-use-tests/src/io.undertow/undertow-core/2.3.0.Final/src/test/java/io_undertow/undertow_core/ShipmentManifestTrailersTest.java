/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.protocol.http.HttpAttachments;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * HTTP request and response trailers with `HttpAttachments` verify streamed shipment manifests.
 *
 * A warehouse uploads chunked manifests with a SHA-256 checksum available only after the last row.
 * The service publishes a staged file only after checking the request trailer. Downloads stream
 * the archived rows and supply their checksum in a response trailer, allowing the recipient to
 * validate the complete transfer. A corrupt or missing checksum must not replace the last manifest.
 * The socket client reads HTTP framing explicitly because the JDK HTTP client does not expose
 * response trailers.
 */
@Timeout(60)
public class ShipmentManifestTrailersTest {

    private static final HttpString CHECKSUM = new HttpString("X-Manifest-Sha256");

    @TempDir
    Path archive;

    private Undertow server;
    private InetSocketAddress address;

    @BeforeEach
    void startManifestArchive() {
        server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(2)
                .addHttpListener(0, "127.0.0.1")
                .setHandler(new BlockingHandler(this::manifest))
                .build();
        server.start();
        address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
    }

    @AfterEach
    void stopManifestArchive() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void validatesTrailingChecksumsBeforePublishingAndDuringDownload() throws Exception {
        byte[] original = ("shipment,sku,quantity\n" + "S-1042,BOX-8,12\n".repeat(1600))
                .getBytes(StandardCharsets.UTF_8);
        String checksum = hex(sha256().digest(original));

        Reply accepted = upload(original, checksum);
        assertEquals(StatusCodes.CREATED, accepted.status());
        assertEquals("Manifest archived", accepted.text());
        assertDownload(original, checksum);

        byte[] replacement = "shipment,sku,quantity\nS-1043,BOX-9,7\n"
                .getBytes(StandardCharsets.UTF_8);
        Reply corrupt = upload(replacement, "0".repeat(64));
        assertEquals(StatusCodes.UNPROCESSABLE_ENTITY, corrupt.status());
        assertEquals("Manifest checksum mismatch", corrupt.text());
        assertDownload(original, checksum);

        Reply missing = upload(replacement, null);
        assertEquals(StatusCodes.BAD_REQUEST, missing.status());
        assertEquals("Manifest checksum trailer required", missing.text());
        assertDownload(original, checksum);
    }

    private void manifest(HttpServerExchange exchange) throws IOException {
        Path published = archive.resolve("current.csv");
        if (!"/manifest".equals(exchange.getRequestPath())) {
            exchange.setStatusCode(StatusCodes.NOT_FOUND);
            exchange.endExchange();
        } else if (exchange.getRequestMethod().equals(Methods.PUT)) {
            Path staged = Files.createTempFile(archive, "incoming-", ".csv");
            try {
                MessageDigest digest = sha256();
                try (OutputStream output = Files.newOutputStream(staged)) {
                    copy(exchange.getInputStream(), output, digest);
                }
                HeaderMap trailers = exchange.getAttachment(HttpAttachments.REQUEST_TRAILERS);
                String supplied = trailers == null ? null : trailers.getFirst(CHECKSUM);
                if (supplied == null) {
                    exchange.setStatusCode(StatusCodes.BAD_REQUEST);
                    exchange.getResponseSender().send("Manifest checksum trailer required");
                } else if (!hex(digest.digest()).equals(supplied)) {
                    exchange.setStatusCode(StatusCodes.UNPROCESSABLE_ENTITY);
                    exchange.getResponseSender().send("Manifest checksum mismatch");
                } else {
                    Files.move(staged, published, StandardCopyOption.REPLACE_EXISTING);
                    exchange.setStatusCode(StatusCodes.CREATED);
                    exchange.getResponseSender().send("Manifest archived");
                }
            } finally {
                Files.deleteIfExists(staged);
            }
        } else if (exchange.getRequestMethod().equals(Methods.GET) && Files.exists(published)) {
            MessageDigest digest = sha256();
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/csv");
            exchange.getResponseHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
            exchange.getResponseHeaders().put(Headers.TRAILER, CHECKSUM.toString());
            exchange.putAttachment(HttpAttachments.RESPONSE_TRAILER_SUPPLIER,
                    () -> new HeaderMap().put(CHECKSUM, hex(digest.digest())));
            try (InputStream input = Files.newInputStream(published);
                    OutputStream output = exchange.getOutputStream()) {
                copy(input, output, digest);
            }
        } else {
            exchange.setStatusCode(StatusCodes.NOT_FOUND);
            exchange.endExchange();
        }
    }

    private static void copy(InputStream input, OutputStream output, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            digest.update(buffer, 0, count);
            output.write(buffer, 0, count);
        }
    }

    private Reply upload(byte[] body, String checksum) throws IOException {
        try (Socket socket = connect()) {
            OutputStream output = socket.getOutputStream();
            ascii(output, "PUT /manifest HTTP/1.1\r\nHost: warehouse.example\r\n"
                    + "Connection: close\r\nTransfer-Encoding: chunked\r\n"
                    + "Content-Type: text/csv\r\nTrailer: X-Manifest-Sha256\r\n\r\n");
            for (int offset = 0; offset < body.length; offset += 3072) {
                int size = Math.min(3072, body.length - offset);
                ascii(output, Integer.toHexString(size) + "\r\n");
                output.write(body, offset, size);
                ascii(output, "\r\n");
            }
            ascii(output, "0\r\n");
            if (checksum != null) {
                ascii(output, "X-Manifest-Sha256: " + checksum + "\r\n");
            }
            ascii(output, "\r\n");
            output.flush();
            return readReply(socket);
        }
    }

    private void assertDownload(byte[] expected, String checksum) throws IOException {
        try (Socket socket = connect()) {
            ascii(socket.getOutputStream(), "GET /manifest HTTP/1.1\r\nHost: warehouse.example\r\n"
                    + "Connection: close\r\nTE: trailers\r\n\r\n");
            socket.getOutputStream().flush();
            Reply reply = readReply(socket);
            assertEquals(StatusCodes.OK, reply.status());
            assertEquals("text/csv", reply.headers().get("Content-Type"));
            assertEquals("chunked", reply.headers().get("Transfer-Encoding"));
            assertEquals(CHECKSUM.toString(), reply.headers().get("Trailer"));
            assertArrayEquals(expected, reply.body());
            assertEquals(checksum, reply.trailers().get(CHECKSUM.toString()));
            assertEquals(hex(sha256().digest(reply.body())), reply.trailers().get(CHECKSUM.toString()));
        }
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

    private static Reply readReply(Socket socket) throws IOException {
        InputStream input = new BufferedInputStream(socket.getInputStream());
        int status = Integer.parseInt(line(input).split(" ")[1]);
        Map<String, String> headers = readHeaders(input);
        Map<String, String> trailers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if ("chunked".equalsIgnoreCase(headers.get("Transfer-Encoding"))) {
            int size;
            while ((size = Integer.parseInt(line(input).split(";", 2)[0], 16)) != 0) {
                byte[] chunk = input.readNBytes(size);
                if (chunk.length != size || !line(input).isEmpty()) {
                    throw new IOException("Incomplete HTTP chunk");
                }
                body.write(chunk);
            }
            trailers = readHeaders(input);
        } else {
            input.transferTo(body);
        }
        return new Reply(status, headers, body.toByteArray(), trailers);
    }

    private static Map<String, String> readHeaders(InputStream input) throws IOException {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String header;
        while (!(header = line(input)).isEmpty()) {
            int colon = header.indexOf(':');
            if (colon < 1) {
                throw new IOException("Malformed HTTP header");
            }
            headers.put(header.substring(0, colon), header.substring(colon + 1).trim());
        }
        return headers;
    }

    private static String line(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int count = 0; count < 8192; count++) {
            int value = input.read();
            if (value == -1) {
                throw new EOFException("Incomplete HTTP line");
            }
            if (value == '\n') {
                return bytes.toString(StandardCharsets.US_ASCII).replace("\r", "");
            }
            bytes.write(value);
        }
        throw new IOException("HTTP line too long");
    }

    private static void ascii(OutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.US_ASCII));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private record Reply(int status, Map<String, String> headers, byte[] body, Map<String, String> trailers) {
        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
