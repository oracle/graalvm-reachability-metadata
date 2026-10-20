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

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jetty's forwarded-request customization subsystem for checkout links behind a TLS-terminating proxy.
public class ProxiedCheckoutTest {

    @Test
    @Timeout(60)
    public void createsPublicOrderLinksAndRecordsTheOriginalClientAddress() throws Exception {
        Server server = new Server();
        HttpConfiguration http = new HttpConfiguration();
        // Only trusted proxies may reach this backend; they replace incoming forwarding headers.
        http.addCustomizer(new ForwardedRequestCustomizer());
        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(http));
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(10000);
        server.addConnector(connector);
        server.setStopTimeout(10000);
        server.setHandler(new CheckoutHandler());

        try {
            server.start();
            URI base = URI.create("http://127.0.0.1:" + connector.getLocalPort());
            URI checkout = base.resolve("/checkout");
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()) {
                HttpResponse<String> forwarded = client.send(checkoutRequest(checkout)
                        .header("Forwarded", "for=203.0.113.9;proto=https;host=\"shop.example.test:8443\"")
                        .build(), HttpResponse.BodyHandlers.ofString());
                assertReceipt(forwarded, "https://shop.example.test:8443/orders/104", "203.0.113.9");

                HttpResponse<String> legacyProxy = client.send(checkoutRequest(checkout)
                        .header("X-Forwarded-For", "198.51.100.27")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "checkout.example.test:9443")
                        .build(), HttpResponse.BodyHandlers.ofString());
                assertReceipt(legacyProxy, "https://checkout.example.test:9443/orders/104", "198.51.100.27");

                // A direct request must not inherit the previous request's proxy information.
                HttpResponse<String> direct = client.send(checkoutRequest(checkout).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertReceipt(direct, base.resolve("/orders/104").toString(), "127.0.0.1");
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }

    private static HttpRequest.Builder checkoutRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody());
    }

    private static void assertReceipt(HttpResponse<String> response, String location, String clientAddress) {
        assertEquals(201, response.statusCode());
        assertEquals(location, response.headers().firstValue("Location").orElseThrow());
        assertEquals("text/plain;charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("Order 104 accepted\nClient address: " + clientAddress + "\n", response.body());
    }

    private static class CheckoutHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"POST".equals(request.getMethod()) || !"/checkout".equals(Request.getPathInContext(request))) {
                return false;
            }
            response.setStatus(201);
            response.getHeaders().put(HttpHeader.LOCATION, Request.newHttpURIFrom(request, "/orders/104").asString());
            response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
            String receipt = "Order 104 accepted\nClient address: " + Request.getRemoteAddr(request) + "\n";
            Content.Sink.write(response, true, receipt, callback);
            return true;
        }
    }
}
