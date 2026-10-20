/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import com.sun.net.httpserver.HttpServer;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HttpClient's multipart request subsystem to upload an invoice with customer and description fields.
public class InvoiceMultipartUploadTest {
    @Test
    @Timeout(60)
    public void uploadsAnInvoiceFileAndUtf8FieldsAndReceivesItsStorageReceipt() throws IOException {
        String customer = "customer-42";
        String description = "Signed invoice for Caf\u00e9 North & South";
        byte[] invoice = "invoice,customer,total\nINV-1042,Caf\u00e9 North,125.50\n"
                .getBytes(StandardCharsets.UTF_8);
        List<ReceivedUpload> uploads = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/invoices", exchange -> {
            try (exchange) {
                uploads.add(new ReceivedUpload(exchange.getRequestMethod(),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        exchange.getRequestBody().readAllBytes()));
                byte[] receipt = "Stored INV-1042 for customer-42".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                exchange.getResponseHeaders().set("Location", "/invoices/INV-1042");
                exchange.sendResponseHeaders(201, receipt.length);
                exchange.getResponseBody().write(receipt);
            }
        });
        server.start();

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(10_000)
                .setConnectionRequestTimeout(10_000)
                .build();
        String uploadUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/invoices";
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(config).build()) {
            ContentType utf8Text = ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8);
            ContentType csv = ContentType.create("text/csv", StandardCharsets.UTF_8);
            HttpPost upload = new HttpPost(uploadUrl);
            upload.setEntity(MultipartEntityBuilder.create()
                    .addTextBody("customer", customer, utf8Text)
                    .addTextBody("description", description, utf8Text)
                    .addBinaryBody("document", invoice, csv, "invoice.csv")
                    .build());
            try (CloseableHttpResponse response = client.execute(upload)) {
                assertEquals(201, response.getStatusLine().getStatusCode());
                assertEquals("/invoices/INV-1042", response.getFirstHeader("Location").getValue());
                assertEquals("Stored INV-1042 for customer-42",
                        EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            }

            assertEquals(1, uploads.size());
            ReceivedUpload received = uploads.getFirst();
            assertEquals("POST", received.method());
            ContentType multipartType = ContentType.parse(received.contentType());
            assertEquals("multipart/form-data", multipartType.getMimeType());
            String boundary = multipartType.getParameter("boundary");
            assertNotNull(boundary);
            assertEquals(customer, new String(part(received.body(), boundary, "customer").body(),
                    StandardCharsets.UTF_8));
            assertEquals(description, new String(part(received.body(), boundary, "description").body(),
                    StandardCharsets.UTF_8));
            UploadedPart document = part(received.body(), boundary, "document");
            assertTrue(document.headers().contains("filename=\"invoice.csv\""));
            assertTrue(document.headers().contains("Content-Type: text/csv; charset=UTF-8"));
            assertArrayEquals(invoice, document.body());
        } finally {
            server.stop(0);
        }
    }

    private static UploadedPart part(byte[] body, String boundary, String name) {
        // ISO-8859-1 preserves each wire byte while locating MIME framing; field values remain UTF-8.
        String wire = new String(body, StandardCharsets.ISO_8859_1);
        String disposition = "Content-Disposition: form-data; name=\"" + name + "\"";
        int headerStart = wire.indexOf(disposition);
        assertTrue(headerStart >= 0, "Missing multipart field: " + name);
        int headerEnd = wire.indexOf("\r\n\r\n", headerStart);
        assertTrue(headerEnd >= 0, "Missing field header terminator: " + name);
        int bodyStart = headerEnd + 4;
        int bodyEnd = wire.indexOf("\r\n--" + boundary, bodyStart);
        assertTrue(bodyEnd >= bodyStart, "Missing multipart boundary after: " + name);
        return new UploadedPart(wire.substring(headerStart, headerEnd),
                wire.substring(bodyStart, bodyEnd).getBytes(StandardCharsets.ISO_8859_1));
    }

    private record ReceivedUpload(String method, String contentType, byte[] body) {
    }

    private record UploadedPart(String headers, byte[] body) {
    }
}
