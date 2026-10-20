/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.GracefulHandler;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jetty's graceful shutdown subsystem to finish an in-flight invoice receipt before maintenance.
public class InvoiceReceiptDrainTest {

    @Test
    @Timeout(60)
    public void drainsAnAcceptedReceiptWhileRejectingNewRequests() throws Exception {
        ReceiptHandler receipts = new ReceiptHandler();
        GracefulHandler maintenance = new GracefulHandler(receipts);
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(10000);
        server.addConnector(connector);
        server.setStopTimeout(10000);
        server.setHandler(maintenance);

        try {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/invoices/104/receipt");
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()) {
                try {
                    CompletableFuture<HttpResponse<String>> pending = client.sendAsync(receiptRequest(endpoint),
                            HttpResponse.BodyHandlers.ofString());
                    assertTrue(receipts.started.await(10, TimeUnit.SECONDS),
                            "The receipt must be accepted before maintenance begins");

                    // A deployment waits for this future before stopping the server or its receipt worker.
                    CompletableFuture<Void> drained = maintenance.shutdown();
                    assertFalse(drained.isDone(), "Maintenance must wait for the accepted receipt");
                    assertEquals(503, client.send(receiptRequest(endpoint),
                            HttpResponse.BodyHandlers.ofString()).statusCode());
                    assertFalse(drained.isDone(), "Rejecting a new request must not discard the existing one");

                    receipts.finish();
                    HttpResponse<String> receipt = pending.get(10, TimeUnit.SECONDS);
                    assertEquals(200, receipt.statusCode());
                    assertEquals("text/plain;charset=utf-8",
                            receipt.headers().firstValue("Content-Type").orElseThrow());
                    assertEquals("Invoice 104\nPaid: USD 125.00\n", receipt.body());
                    drained.get(10, TimeUnit.SECONDS);

                    // Draining does not reopen the application while deployment cleanup is still running.
                    assertEquals(503, client.send(receiptRequest(endpoint),
                            HttpResponse.BodyHandlers.ofString()).statusCode());
                } finally {
                    receipts.finish();
                    client.shutdownNow();
                }
            }
        } finally {
            receipts.finish();
            server.stop();
            server.destroy();
        }
    }

    private static HttpRequest receiptRequest(URI endpoint) {
        return HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30)).GET().build();
    }

    private static class ReceiptHandler extends Handler.Abstract {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CompletableFuture<String> generatedReceipt = new CompletableFuture<>();

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"GET".equals(request.getMethod())
                    || !"/invoices/104/receipt".equals(Request.getPathInContext(request))) {
                return false;
            }
            // Receipt generation completes asynchronously without holding a Jetty request thread.
            generatedReceipt.thenAccept(body -> {
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
                Content.Sink.write(response, true, body, callback);
            });
            started.countDown();
            return true;
        }

        private void finish() {
            generatedReceipt.complete("Invoice 104\nPaid: USD 125.00\n");
        }
    }
}
