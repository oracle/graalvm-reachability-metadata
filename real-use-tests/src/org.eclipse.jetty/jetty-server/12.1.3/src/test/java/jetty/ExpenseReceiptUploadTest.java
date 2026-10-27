/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MultiPart;
import org.eclipse.jetty.http.MultiPartConfig;
import org.eclipse.jetty.http.MultiPartFormData;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jetty's multipart upload subsystem to store an expense receipt with its accompanying form fields.
public class ExpenseReceiptUploadTest {

    @Test
    @Timeout(60)
    public void storesUploadedReceiptAndRejectsSubmissionWithoutAFile(@TempDir Path directory) throws Exception {
        Path spool = Files.createDirectory(directory.resolve("spool"));
        Path receipts = Files.createDirectory(directory.resolve("receipts"));
        Path savedReceipt = receipts.resolve("expense-104.csv");
        String receipt = "item,amount\nTrain ticket,45.00\nLunch,12.50\n";

        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(10000);
        server.addConnector(connector);
        server.setStopTimeout(10000);
        server.setHandler(new ReceiptHandler(spool, savedReceipt));

        try {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/expenses/104/receipt");
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()) {
                String upload = String.join("\r\n",
                        "--ExpenseBoundary",
                        "Content-Disposition: form-data; name=\"description\"",
                        "",
                        "Customer visit",
                        "--ExpenseBoundary",
                        "Content-Disposition: form-data; name=\"receipt\"; filename=\"travel.csv\"",
                        "Content-Type: text/csv",
                        "",
                        receipt,
                        "--ExpenseBoundary--",
                        "");
                HttpResponse<String> accepted = submit(client, endpoint, upload);
                assertEquals(201, accepted.statusCode());
                assertEquals("text/plain;charset=utf-8",
                        accepted.headers().firstValue("Content-Type").orElseThrow());
                assertEquals("/expenses/104/receipt",
                        accepted.headers().firstValue("Location").orElseThrow());
                assertEquals("Customer visit: travel.csv (text/csv)\n", accepted.body());
                assertEquals(receipt, Files.readString(savedReceipt));

                String incomplete = String.join("\r\n",
                        "--ExpenseBoundary",
                        "Content-Disposition: form-data; name=\"description\"",
                        "",
                        "Missing attachment",
                        "--ExpenseBoundary--",
                        "");
                HttpResponse<String> rejected = submit(client, endpoint, incomplete);
                assertEquals(422, rejected.statusCode());
                assertEquals("A description and receipt file are required\n", rejected.body());
                assertEquals(receipt, Files.readString(savedReceipt));
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }

    private static HttpResponse<String> submit(HttpClient client, URI endpoint, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "multipart/form-data; boundary=ExpenseBoundary")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static class ReceiptHandler extends Handler.Abstract {
        private final MultiPartConfig config;
        private final Path savedReceipt;

        private ReceiptHandler(Path spool, Path savedReceipt) {
            this.savedReceipt = savedReceipt;
            config = new MultiPartConfig.Builder()
                    .location(spool)
                    .maxParts(4)
                    .maxSize(65536)
                    .maxPartSize(32768)
                    .maxMemoryPartSize(16)
                    .build();
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"/expenses/104/receipt".equals(Request.getPathInContext(request))
                    || !"POST".equals(request.getMethod())) {
                return false;
            }
            MultiPartFormData.onParts(request, request, request.getHeaders().get(HttpHeader.CONTENT_TYPE), config,
                    Promise.Invocable.from(getInvocationType(), parts -> {
                        try (parts) {
                            MultiPart.Part description = parts.getFirst("description");
                            MultiPart.Part receipt = parts.getFirst("receipt");
                            response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
                            if (description == null || receipt == null || receipt.getFileName() == null) {
                                response.setStatus(422);
                                Content.Sink.write(response, true,
                                        "A description and receipt file are required\n", callback);
                                return;
                            }
                            String label = description.getContentAsString(StandardCharsets.UTF_8);
                            // The application chooses the destination rather than trusting the supplied filename.
                            receipt.writeTo(savedReceipt);
                            response.setStatus(201);
                            response.getHeaders().put(HttpHeader.LOCATION, "/expenses/104/receipt");
                            String confirmation = label + ": " + receipt.getFileName() + " ("
                                    + receipt.getHeaders().get(HttpHeader.CONTENT_TYPE) + ")\n";
                            Content.Sink.write(response, true, confirmation, callback);
                        } catch (IOException failure) {
                            callback.failed(failure);
                        }
                    }, callback::failed));
            return true;
        }
    }
}
