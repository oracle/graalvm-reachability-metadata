/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Session;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.session.SessionHandler;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jetty's session management subsystem for isolated shopping carts and logout invalidation.
public class ShoppingCartSessionTest {

    @Test
    @Timeout(60)
    public void keepsEachShoppersCartUntilLogout() throws Exception {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(10000);
        server.addConnector(connector);
        server.setStopTimeout(10000);

        SessionHandler sessions = new SessionHandler();
        sessions.setHttpOnly(true);
        sessions.setSameSite(HttpCookie.SameSite.LAX);
        sessions.setHandler(new CartHandler());
        ContextHandler shop = new ContextHandler("/shop");
        shop.setHandler(sessions);
        server.setHandler(shop);

        try {
            server.start();
            URI base = URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/shop/");
            try (HttpClient shopper = newBrowser(); HttpClient otherShopper = newBrowser()) {
                HttpResponse<String> empty = send(shopper, base.resolve("cart"), "GET");
                assertEquals(200, empty.statusCode());
                assertEquals("Your cart is empty\n", empty.body());
                assertTrue(empty.headers().allValues("Set-Cookie").isEmpty());

                HttpResponse<String> firstItem = send(shopper, base.resolve("cart/add"), "POST");
                assertEquals(200, firstItem.statusCode());
                assertEquals("Desk lamps: 1\n", firstItem.body());
                String cookie = firstItem.headers().firstValue("Set-Cookie").orElseThrow();
                assertTrue(cookie.startsWith("JSESSIONID="));
                String attributes = cookie.toLowerCase(Locale.ROOT);
                assertTrue(attributes.contains("path=/shop"));
                assertTrue(attributes.contains("httponly"));
                assertTrue(attributes.contains("samesite=lax"));

                assertCart(shopper, base, "POST", "cart/add", "Desk lamps: 2\n");
                assertCart(shopper, base, "GET", "cart", "Desk lamps: 2\n");
                assertCart(otherShopper, base, "GET", "cart", "Your cart is empty\n");
                assertCart(otherShopper, base, "POST", "cart/add", "Desk lamps: 1\n");
                assertCart(shopper, base, "GET", "cart", "Desk lamps: 2\n");

                assertCart(shopper, base, "POST", "logout", "Signed out\n");
                assertCart(shopper, base, "GET", "cart", "Your cart is empty\n");
                assertCart(otherShopper, base, "GET", "cart", "Desk lamps: 1\n");
                assertCart(shopper, base, "POST", "cart/add", "Desk lamps: 1\n");
                assertCart(shopper, base, "GET", "cart", "Desk lamps: 1\n");
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }

    private static HttpClient newBrowser() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    private static HttpResponse<String> send(HttpClient client, URI uri, String method) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertCart(HttpClient client, URI base, String method, String path, String body)
            throws Exception {
        HttpResponse<String> response = send(client, base.resolve(path), method);
        assertEquals(200, response.statusCode());
        assertEquals(body, response.body());
    }

    private static class CartHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            String path = Request.getPathInContext(request);
            String body;
            if ("/cart/add".equals(path) && "POST".equals(request.getMethod())) {
                Session session = request.getSession(true);
                Integer count = (Integer) session.getAttribute("lampCount");
                int quantity = count == null ? 1 : count + 1;
                session.setAttribute("lampCount", quantity);
                body = "Desk lamps: " + quantity + "\n";
            } else if ("/cart".equals(path) && "GET".equals(request.getMethod())) {
                Session session = request.getSession(false);
                Object quantity = session == null ? null : session.getAttribute("lampCount");
                body = quantity == null ? "Your cart is empty\n" : "Desk lamps: " + quantity + "\n";
            } else if ("/logout".equals(path) && "POST".equals(request.getMethod())) {
                Session session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                body = "Signed out\n";
            } else {
                return false;
            }
            response.setStatus(200);
            response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
            Content.Sink.write(response, true, body, callback);
            return true;
        }
    }
}
