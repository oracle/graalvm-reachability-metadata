/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Accepts expense-receipt uploads through multipart form-data handling, where a servlet
 * `Wrapper` carries a `MultipartConfigElement` and the request exposes each `Part` that
 * Tomcat parsed, buffered in memory or spilled to the upload location, within the
 * configured file-size, request-size and connector part-count limits.
 *
 * An expense tool lets employees submit a claim form with the claimant's name and one or
 * more scanned receipts. The servlet reads the form field through `getParameter`, digests
 * every uploaded file, keeps it under a server-chosen name with `Part.write` and reports
 * the submitted file name, content type and size it saw. Receipts larger than the
 * per-file limit and forms with more parts than the connector allows are refused with
 * `413` before anything is stored, and the temporary files Tomcat spilled are gone once
 * the request has been recycled.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpenseReceiptMultipartUploadTest {

    private static final long MAX_FILE_SIZE = 64 * 1024;

    private static final long MAX_REQUEST_SIZE = 256 * 1024;

    private static final int FILE_SIZE_THRESHOLD = 1024;

    private static final int MAX_PART_COUNT = 5;

    private static final String BOUNDARY = "----ExpenseClaimBoundary7d1f";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ReceiptServlet receiptServlet = new ReceiptServlet();

    private Tomcat tomcat;

    private Path baseDir;

    private Path uploadDir;

    private String baseUrl;

    @BeforeAll
    void startServer() throws IOException, LifecycleException {
        baseDir = Files.createTempDirectory("expense-tomcat");
        uploadDir = Files.createDirectories(baseDir.resolve("receipts"));
        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        tomcat.getConnector().setMaxPartCount(MAX_PART_COUNT);

        Context context = tomcat.addContext("/expenses", baseDir.toString());
        Wrapper wrapper = Tomcat.addServlet(context, "receipts", receiptServlet);
        wrapper.setMultipartConfigElement(new MultipartConfigElement(
                uploadDir.toString(), MAX_FILE_SIZE, MAX_REQUEST_SIZE, FILE_SIZE_THRESHOLD));
        context.addServletMappingDecoded("/claims", "receipts");

        tomcat.start();
        baseUrl = "http://localhost:" + tomcat.getConnector().getLocalPort() + "/expenses";
    }

    @AfterAll
    void stopServer() throws LifecycleException, IOException {
        tomcat.stop();
        tomcat.destroy();
        try (Stream<Path> paths = Files.walk(baseDir)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void storesSmallAndSpilledReceiptsWithTheirClaimant() throws Exception {
        byte[] taxiReceipt = "Taxi 2026-10-14 Airport -> Office EUR 38.50\n".getBytes(StandardCharsets.UTF_8);
        byte[] hotelScan = pseudoScan(20_000, 42L);

        MultipartForm form = new MultipartForm()
                .field("claimant", "J\u00f6rg Fran\u00e7ois")
                .file("receipt", "taxi.txt", "text/plain", taxiReceipt)
                .file("receipt", "hotel-scan.png", "image/png", hotelScan);
        HttpResponse<String> response = send(form);

        assertThat(response.statusCode()).isEqualTo(201);
        List<String> lines = response.body().lines().toList();
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("claimant=J\u00f6rg Fran\u00e7ois");

        String[] taxi = lines.get(1).split(" ");
        assertThat(taxi).startsWith("name=taxi.txt", "type=text/plain",
                "size=" + taxiReceipt.length, "sha256=" + sha256(taxiReceipt));
        String[] hotel = lines.get(2).split(" ");
        assertThat(hotel).startsWith("name=hotel-scan.png", "type=image/png",
                "size=" + hotelScan.length, "sha256=" + sha256(hotelScan));

        assertThat(Files.readAllBytes(uploadDir.resolve(storedName(taxi)))).isEqualTo(taxiReceipt);
        assertThat(Files.readAllBytes(uploadDir.resolve(storedName(hotel)))).isEqualTo(hotelScan);
        awaitNoSpilledTempFiles();
    }

    @Test
    void decodesAUtf8SubmittedFileName() throws Exception {
        byte[] invoice = "Caf\u00e9 lunch EUR 12.00\n".getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> response = send(new MultipartForm()
                .field("claimant", "Ana")
                .file("receipt", "caf\u00e9-r\u00e9sum\u00e9.txt", "text/plain", invoice));

        assertThat(response.statusCode()).isEqualTo(201);
        List<String> lines = response.body().lines().toList();
        assertThat(lines).containsExactly(
                "claimant=Ana",
                "name=caf\u00e9-r\u00e9sum\u00e9.txt type=text/plain size=" + invoice.length
                        + " sha256=" + sha256(invoice) + " stored=" + storedName(lines.get(1).split(" ")));
    }

    @Test
    void refusesAReceiptLargerThanTheFileSizeLimit() throws Exception {
        int rejectedBefore = receiptServlet.rejected.get();
        long storedBefore = storedReceiptCount();

        HttpResponse<String> response = send(new MultipartForm()
                .field("claimant", "Bob")
                .file("receipt", "conference-invoice.pdf", "application/pdf",
                        pseudoScan((int) MAX_FILE_SIZE + 4096, 7L)));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(receiptServlet.rejected.get()).isEqualTo(rejectedBefore + 1);
        assertThat(storedReceiptCount()).isEqualTo(storedBefore);
    }

    @Test
    void refusesAFormWithMorePartsThanTheConnectorAllows() throws Exception {
        int rejectedBefore = receiptServlet.rejected.get();
        long storedBefore = storedReceiptCount();

        MultipartForm form = new MultipartForm().field("claimant", "Carol");
        for (int i = 1; i <= MAX_PART_COUNT; i++) {
            form.file("receipt", "parking-" + i + ".txt", "text/plain",
                    ("Parking ticket " + i + "\n").getBytes(StandardCharsets.UTF_8));
        }
        HttpResponse<String> response = send(form);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(receiptServlet.rejected.get()).isEqualTo(rejectedBefore + 1);
        assertThat(storedReceiptCount()).isEqualTo(storedBefore);
    }

    private HttpResponse<String> send(MultipartForm form) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/claims"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(form.toBytes()))
                .timeout(Duration.ofSeconds(10))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private long storedReceiptCount() throws IOException {
        try (Stream<Path> files = Files.list(uploadDir)) {
            return files.filter(path -> path.getFileName().toString().startsWith("receipt-")).count();
        }
    }

    /** Polls until the request that spilled a part has been recycled and its temp file removed. */
    private void awaitNoSpilledTempFiles() throws IOException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        List<String> temporary = spilledTempFiles();
        while (!temporary.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(50);
            temporary = spilledTempFiles();
        }
        assertThat(temporary).isEmpty();
    }

    private List<String> spilledTempFiles() throws IOException {
        try (Stream<Path> files = Files.list(uploadDir)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".tmp"))
                    .toList();
        }
    }

    private static String storedName(String[] receiptLine) {
        String stored = receiptLine[receiptLine.length - 1];
        assertThat(stored).startsWith("stored=receipt-");
        return stored.substring("stored=".length());
    }

    private static byte[] pseudoScan(int size, long seed) {
        byte[] bytes = new byte[size];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Builds a `multipart/form-data` body the way a browser submits the claim form. */
    static final class MultipartForm {

        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        MultipartForm field(String name, String value) {
            header("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
            write("\r\n" + value + "\r\n");
            return this;
        }

        MultipartForm file(String name, String fileName, String contentType, byte[] content) {
            header("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: " + contentType + "\r\n");
            write("\r\n");
            body.writeBytes(content);
            write("\r\n");
            return this;
        }

        byte[] toBytes() {
            ByteArrayOutputStream complete = new ByteArrayOutputStream();
            complete.writeBytes(body.toByteArray());
            complete.writeBytes(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return complete.toByteArray();
        }

        private void header(String headers) {
            write("--" + BOUNDARY + "\r\n" + headers);
        }

        private void write(String text) {
            body.writeBytes(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Digests and stores every uploaded receipt and answers with what it received. */
    static final class ReceiptServlet extends HttpServlet {

        private final AtomicInteger stored = new AtomicInteger();

        private final AtomicInteger rejected = new AtomicInteger();

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            request.setCharacterEncoding(StandardCharsets.UTF_8.name());
            List<Part> receipts = new ArrayList<>();
            try {
                for (Part part : request.getParts()) {
                    if (part.getSubmittedFileName() != null) {
                        receipts.add(part);
                    }
                }
            } catch (IllegalStateException | ServletException e) {
                rejected.incrementAndGet();
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                return;
            }

            response.setStatus(HttpServletResponse.SC_CREATED);
            response.setContentType("text/plain");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            PrintWriter writer = response.getWriter();
            writer.println("claimant=" + request.getParameter("claimant"));
            for (Part receipt : receipts) {
                String digest;
                try (InputStream content = receipt.getInputStream()) {
                    digest = sha256(content.readAllBytes());
                }
                String storedName = "receipt-" + stored.incrementAndGet() + ".bin";
                receipt.write(storedName);
                writer.println("name=" + receipt.getSubmittedFileName()
                        + " type=" + receipt.getContentType()
                        + " size=" + receipt.getSize()
                        + " sha256=" + digest
                        + " stored=" + storedName);
            }
        }
    }
}
