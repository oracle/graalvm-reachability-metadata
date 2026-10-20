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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Jetty's access logging subsystem to correlate order requests without recording query-string secrets.
public class OrderAccessLogTest {

    @Test
    @Timeout(60)
    public void logsSuccessfulAndMissingOrdersWithTheirRequestIds() throws Exception {
        BlockingQueue<String> logLines = new LinkedBlockingQueue<>();
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(10000);
        server.addConnector(connector);
        server.setStopTimeout(10000);
        server.setHandler(new OrderHandler());
        // Log the URL path, not the query containing the customer's download token.
        server.setRequestLog(new CustomRequestLog(logLines::add, "%m %U %s %{X-Request-ID}i"));

        try {
            server.start();
            URI base = URI.create("http://127.0.0.1:" + connector.getLocalPort());
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()) {
                HttpResponse<String> order = get(client,
                        base.resolve("/orders/104?downloadToken=private-customer-token"), "order-104");
                assertEquals(200, order.statusCode());
                assertEquals("Order 104: ready for collection\n", order.body());
                assertEquals("text/plain;charset=utf-8",
                        order.headers().firstValue("Content-Type").orElseThrow());
                assertLogLine(logLines, "GET /orders/104 200 order-104");

                HttpResponse<String> missing = get(client,
                        base.resolve("/orders/999?downloadToken=another-private-token"), "missing-order");
                assertEquals(404, missing.statusCode());
                assertLogLine(logLines, "GET /orders/999 404 missing-order");
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }

    private static HttpResponse<String> get(HttpClient client, URI uri, String requestId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("X-Request-ID", requestId)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertLogLine(BlockingQueue<String> logLines, String expected) throws InterruptedException {
        String line = logLines.poll(10, TimeUnit.SECONDS);
        assertNotNull(line, "The completed request must produce an access log entry");
        assertEquals(expected, line);
    }

    private static class OrderHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"GET".equals(request.getMethod()) || !"/orders/104".equals(Request.getPathInContext(request))) {
                return false;
            }
            response.setStatus(200);
            response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
            Content.Sink.write(response, true, "Order 104: ready for collection\n", callback);
            return true;
        }
    }
}
