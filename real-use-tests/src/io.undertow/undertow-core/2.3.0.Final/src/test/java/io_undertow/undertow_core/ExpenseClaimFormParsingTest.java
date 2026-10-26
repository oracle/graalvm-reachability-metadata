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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.form.EagerFormParsingHandler;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormEncodedDataDefinition;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.form.MultiPartParserDefinition;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Deque;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * HTTP form parsing with `FormParserFactory`: URL-encoded bodies parsed ahead of the handler by
 * `EagerFormParsingHandler`, and `multipart/form-data` uploads parsed with a blocking
 * `FormDataParser` whose file parts are kept in memory or spooled to disk by size.
 *
 * An expense-claim service runs on an embedded Undertow server. Employees file a claim through
 * an HTML-style form with repeated and UTF-8 encoded fields, then upload receipts: a small CSV
 * stays in memory, a larger scan is spooled to a temporary directory and archived with
 * `FileItem.write`, an upload over the per-file limit is refused with 413, and a body that is no
 * form at all is refused with 415. The client is the JDK `HttpClient`.
 */
public class ExpenseClaimFormParsingTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BOUNDARY = "claims-boundary-7d1f";
    private static final long IN_MEMORY_THRESHOLD = 1024;
    private static final long MAX_FILE_SIZE = 8192;

    @TempDir
    Path spool;

    @TempDir
    Path archive;

    private Undertow server;
    private HttpClient client;
    private URI base;

    @BeforeEach
    void startServer() {
        FormParserFactory urlEncoded = FormParserFactory.builder(false)
                .addParser(new FormEncodedDataDefinition())
                .withDefaultCharset("UTF-8")
                .build();
        MultiPartParserDefinition multipart = new MultiPartParserDefinition(spool);
        multipart.setFileSizeThreshold(IN_MEMORY_THRESHOLD);
        multipart.setMaxIndividualFileSize(MAX_FILE_SIZE);
        FormParserFactory uploads = FormParserFactory.builder(false)
                .addParser(multipart)
                .withDefaultCharset("UTF-8")
                .build();

        server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setHandler(Handlers.path()
                        .addExactPath("/claims", new EagerFormParsingHandler(urlEncoded)
                                .setNext(ExpenseClaimFormParsingTest::fileClaim))
                        .addExactPath("/receipts", new BlockingHandler(
                                new ReceiptDesk(uploads, archive)::accept)))
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        base = URI.create("http://127.0.0.1:" + address.getPort());
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void parsesRepeatedAndUtf8FieldsOfAnUrlEncodedClaim() throws Exception {
        HttpResponse<String> response = post("/claims", "application/x-www-form-urlencoded",
                ("employee=Ren%C3%A9e+Dupont&amount=42.50&category=travel&category=meals"
                        + "&note=Taxi+%26+lunch+in+Z%C3%BCrich").getBytes(StandardCharsets.US_ASCII));

        assertEquals(201, response.statusCode());
        JsonNode claim = JSON.readTree(response.body());
        assertEquals("Ren\u00e9e Dupont", claim.get("employee").asText());
        assertEquals("42.50", claim.get("amount").asText());
        assertEquals("[\"travel\",\"meals\"]", claim.get("categories").toString());
        assertEquals("Taxi & lunch in Z\u00fcrich", claim.get("note").asText());
    }

    @Test
    void rejectsAClaimWithoutTheRequiredFields() throws Exception {
        HttpResponse<String> response = post("/claims", "application/x-www-form-urlencoded",
                "employee=Sam".getBytes(StandardCharsets.US_ASCII));

        assertEquals(400, response.statusCode());
        assertEquals("amount is required", JSON.readTree(response.body()).get("error").asText());
    }

    @Test
    void keepsSmallReceiptsInMemoryAndSpoolsLargeScansToDisk() throws Exception {
        byte[] csv = """
                date,merchant,total
                2026-10-02,Cafe Central,18.40
                """.getBytes(StandardCharsets.UTF_8);
        byte[] scan = new byte[4096];
        for (int i = 0; i < scan.length; i++) {
            scan[i] = (byte) (i * 31);
        }
        MultipartBody body = new MultipartBody()
                .field("claim", "CL-1007")
                .field("comment", "Caf\u00e9 receipt and scan")
                .file("receipt", "lunch.csv", "text/csv", csv)
                .file("scan", "lunch-scan.bin", "application/octet-stream", scan);

        HttpResponse<String> response = post("/receipts", body.contentType(), body.bytes());

        assertEquals(200, response.statusCode());
        JsonNode stored = JSON.readTree(response.body());
        assertEquals("CL-1007", stored.get("claim").asText());
        assertEquals("Caf\u00e9 receipt and scan", stored.get("comment").asText());

        JsonNode receipt = stored.get("files").get(0);
        assertEquals("receipt", receipt.get("field").asText());
        assertEquals("lunch.csv", receipt.get("fileName").asText());
        assertEquals("text/csv", receipt.get("contentType").asText());
        assertEquals(csv.length, receipt.get("size").asLong());
        assertTrue(receipt.get("inMemory").asBoolean());
        assertEquals('d', receipt.get("firstByte").asInt());

        JsonNode spooled = stored.get("files").get(1);
        assertEquals("scan", spooled.get("field").asText());
        assertEquals("lunch-scan.bin", spooled.get("fileName").asText());
        assertEquals("application/octet-stream", spooled.get("contentType").asText());
        assertEquals(scan.length, spooled.get("size").asLong());
        assertFalse(spooled.get("inMemory").asBoolean());
        assertEquals(scan[0] & 0xff, spooled.get("firstByte").asInt());

        assertArrayEquals(csv, Files.readAllBytes(archive.resolve("CL-1007-lunch.csv")));
        assertArrayEquals(scan, Files.readAllBytes(archive.resolve("CL-1007-lunch-scan.bin")));
        try (Stream<Path> leftovers = Files.list(spool)) {
            assertEquals(0, leftovers.count(), "the archived scan was moved out of the spool");
        }
    }

    @Test
    void refusesOversizedUploadsAndBodiesThatAreNoForm() throws Exception {
        byte[] tooLarge = new byte[(int) MAX_FILE_SIZE + 2048];
        Arrays.fill(tooLarge, (byte) 'x');
        MultipartBody body = new MultipartBody()
                .field("claim", "CL-1008")
                .file("scan", "huge.bin", "application/octet-stream", tooLarge);

        HttpResponse<String> oversized = post("/receipts", body.contentType(), body.bytes());
        assertEquals(413, oversized.statusCode());
        assertEquals("receipt exceeds 8192 bytes",
                JSON.readTree(oversized.body()).get("error").asText());

        HttpResponse<String> notAForm = post("/receipts", "application/json",
                "{\"claim\": \"CL-1009\"}".getBytes(StandardCharsets.US_ASCII));
        assertEquals(415, notAForm.statusCode());
        assertEquals("expected multipart/form-data",
                JSON.readTree(notAForm.body()).get("error").asText());

        try (Stream<Path> archived = Files.list(archive)) {
            assertEquals(0, archived.count());
        }
    }

    private HttpResponse<String> post(String path, String contentType, byte[] body)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /// Files a claim from the form data `EagerFormParsingHandler` attached before dispatching here.
    private static void fileClaim(HttpServerExchange exchange) {
        FormData form = exchange.getAttachment(FormDataParser.FORM_DATA);
        if (form == null || !form.contains("employee") || !form.contains("amount")) {
            error(exchange, StatusCodes.BAD_REQUEST,
                    (form != null && form.contains("employee") ? "amount" : "employee") + " is required");
            return;
        }
        ObjectNode claim = JSON.createObjectNode()
                .put("employee", form.getFirst("employee").getValue())
                .put("amount", form.getFirst("amount").getValue());
        ArrayNode categories = claim.putArray("categories");
        Deque<FormData.FormValue> values = form.get("category");
        if (values != null) {
            values.forEach(value -> categories.add(value.getValue()));
        }
        if (form.contains("note")) {
            claim.put("note", form.getFirst("note").getValue());
        }
        json(exchange, StatusCodes.CREATED, claim);
    }

    private static void json(HttpServerExchange exchange, int status, JsonNode body) {
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=UTF-8");
        exchange.getResponseSender().send(body.toString(), StandardCharsets.UTF_8);
    }

    private static void error(HttpServerExchange exchange, int status, String message) {
        json(exchange, status, JSON.createObjectNode().put("error", message));
    }

    /// Accepts receipt uploads on a worker thread, where `BlockingHandler` lets it parse blocking.
    private static final class ReceiptDesk {

        private final FormParserFactory uploads;
        private final Path archive;

        ReceiptDesk(FormParserFactory uploads, Path archive) {
            this.uploads = uploads;
            this.archive = archive;
        }

        void accept(HttpServerExchange exchange) throws IOException {
            try (FormDataParser parser = uploads.createParser(exchange)) {
                if (parser == null) {
                    error(exchange, StatusCodes.UNSUPPORTED_MEDIA_TYPE, "expected multipart/form-data");
                    return;
                }
                FormData form;
                try {
                    form = parser.parseBlocking();
                } catch (MultiPartParserDefinition.FileTooLargeException e) {
                    error(exchange, StatusCodes.REQUEST_ENTITY_TOO_LARGE,
                            "receipt exceeds " + MAX_FILE_SIZE + " bytes");
                    return;
                }
                String claim = form.getFirst("claim").getValue();
                ObjectNode stored = JSON.createObjectNode()
                        .put("claim", claim)
                        .put("comment", form.getFirst("comment").getValue());
                ArrayNode files = stored.putArray("files");
                for (String field : form) {
                    for (FormData.FormValue value : form.get(field)) {
                        if (value.isFileItem()) {
                            files.add(store(claim, field, value));
                        }
                    }
                }
                json(exchange, StatusCodes.OK, stored);
            }
        }

        /// Archives one uploaded file under the claim and describes what arrived.
        private ObjectNode store(String claim, String field, FormData.FormValue value) throws IOException {
            FormData.FileItem item = value.getFileItem();
            ObjectNode described = JSON.createObjectNode()
                    .put("field", field)
                    .put("fileName", value.getFileName())
                    .put("contentType", value.getHeaders().getFirst(Headers.CONTENT_TYPE))
                    .put("size", item.getFileSize())
                    .put("inMemory", item.isInMemory());
            try (InputStream content = item.getInputStream()) {
                described.put("firstByte", content.read());
            }
            item.write(archive.resolve(claim + "-" + value.getFileName()));
            return described;
        }
    }

    /// Builds a `multipart/form-data` request body the way a browser encodes a file form.
    private static final class MultipartBody {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        MultipartBody field(String name, String value) {
            part("Content-Disposition: form-data; name=\"" + name + "\"\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n",
                    value.getBytes(StandardCharsets.UTF_8));
            return this;
        }

        MultipartBody file(String name, String fileName, String contentType, byte[] content) {
            part("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: " + contentType + "\r\n", content);
            return this;
        }

        String contentType() {
            return "multipart/form-data; boundary=" + BOUNDARY;
        }

        byte[] bytes() {
            out.writeBytes(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
            return out.toByteArray();
        }

        private void part(String headers, byte[] content) {
            out.writeBytes(("--" + BOUNDARY + "\r\n" + headers + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.writeBytes(content);
            out.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
    }
}
