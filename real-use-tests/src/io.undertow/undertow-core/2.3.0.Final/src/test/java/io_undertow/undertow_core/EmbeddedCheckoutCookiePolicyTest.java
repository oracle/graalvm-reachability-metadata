/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.handlers.SameSiteCookieHandler;
import io.undertow.util.Headers;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Response-cookie security policies with `SameSiteCookieHandler` support an embedded checkout.
 *
 * A checkout displayed inside a merchant's cross-site frame needs a SameSite=None correlation
 * cookie, while its currency preference uses Lax. TLS terminates at the deployment's edge; the
 * loopback backend emits Secure cookies even though this test connects to it over HTTP. Older
 * browsers that misinterpret None receive the compatibility fallback, without losing Secure or
 * HttpOnly. Completing checkout expires its correlation cookie with the same path and policy.
 * The assertions inspect Set-Cookie on the wire, not a simulated browser's enforcement rules.
 */
@Timeout(60)
public class EmbeddedCheckoutCookiePolicyTest {

    private static final String MODERN_BROWSER = "Mozilla/5.0 (X11; Linux x86_64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String LEGACY_BROWSER = "Mozilla/5.0 (iPhone; CPU iPhone OS 12_4 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.1.2 Mobile/15E148 Safari/604.1";

    private Undertow server;
    private HttpClient client;
    private URI base;

    @BeforeEach
    void startCheckout() {
        SameSiteCookieHandler policies = new SameSiteCookieHandler(
                new SameSiteCookieHandler(
                        Handlers.routing()
                                .post("/checkout/start", exchange -> issueCookies(exchange, false))
                                .post("/checkout/complete", exchange -> issueCookies(exchange, true)),
                        "Lax", "display_currency"),
                "None", "checkout_ref");
        server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(2)
                .addHttpListener(0, "127.0.0.1")
                .setHandler(policies)
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        base = URI.create("http://127.0.0.1:" + address.getPort());
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterEach
    void stopCheckout() {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    void givesTheEmbeddedCheckoutACrossSiteCookieWithoutBroadeningThePreferenceCookie() throws Exception {
        HttpResponse<String> response = post("/checkout/start", MODERN_BROWSER);

        assertEquals(200, response.statusCode());
        assertEquals("Checkout ready for order-42", response.body());
        assertEquals(2, response.headers().allValues("Set-Cookie").size());
        List<String> checkout = cookieParts(response, "checkout_ref");
        assertEquals("checkout_ref=order-42", checkout.getFirst());
        assertTrue(checkout.contains("path=/checkout"));
        assertTrue(checkout.contains("samesite=none"));
        assertTrue(checkout.contains("secure"));
        assertTrue(checkout.contains("httponly"));
        assertPreferenceCookie(response);
    }

    @Test
    void omitsNoneForIncompatibleBrowsersButPreservesOtherCookieProtections() throws Exception {
        HttpResponse<String> response = post("/checkout/start", LEGACY_BROWSER);

        assertEquals(200, response.statusCode());
        assertEquals("Checkout ready for order-42", response.body());
        List<String> checkout = cookieParts(response, "checkout_ref");
        assertEquals("checkout_ref=order-42", checkout.getFirst());
        assertFalse(checkout.stream().anyMatch(part -> part.startsWith("samesite=")));
        assertTrue(checkout.contains("path=/checkout"));
        assertTrue(checkout.contains("secure"));
        assertTrue(checkout.contains("httponly"));
        assertPreferenceCookie(response);

        HttpResponse<String> modern = post("/checkout/start", MODERN_BROWSER);
        assertEquals(200, modern.statusCode());
        assertTrue(cookieParts(modern, "checkout_ref").contains("samesite=none"));
    }

    @Test
    void appliesTheSamePolicyWhenExpiringTheCheckoutCookie() throws Exception {
        HttpResponse<String> started = post("/checkout/start", MODERN_BROWSER);
        assertEquals(200, started.statusCode());

        HttpResponse<String> completed = post("/checkout/complete", MODERN_BROWSER);

        assertEquals(200, completed.statusCode());
        assertEquals("Checkout complete", completed.body());
        List<String> checkout = cookieParts(completed, "checkout_ref");
        assertEquals("checkout_ref=\"\"", checkout.getFirst());
        assertTrue(checkout.contains("max-age=0"));
        assertTrue(checkout.contains("path=/checkout"));
        assertTrue(checkout.contains("samesite=none"));
        assertTrue(checkout.contains("secure"));
        assertTrue(checkout.contains("httponly"));
        assertPreferenceCookie(completed);
    }

    private static void issueCookies(HttpServerExchange exchange, boolean completed) {
        CookieImpl checkout = new CookieImpl("checkout_ref", completed ? "" : "order-42")
                .setPath("/checkout")
                .setSecure(true)
                .setHttpOnly(true);
        if (completed) {
            checkout.setMaxAge(0);
        }
        exchange.setResponseCookie(checkout);
        exchange.setResponseCookie(new CookieImpl("display_currency", "EUR")
                .setPath("/")
                .setSecure(true));
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
        exchange.getResponseSender().send(completed ? "Checkout complete" : "Checkout ready for order-42");
    }

    private HttpResponse<String> post(String path, String userAgent) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", userAgent)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static List<String> cookieParts(HttpResponse<String> response, String name) {
        String header = response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "="))
                .findFirst()
                .orElseThrow();
        return Arrays.stream(header.split(";"))
                .map(String::trim)
                .map(part -> part.toLowerCase(Locale.ROOT))
                .toList();
    }

    private static void assertPreferenceCookie(HttpResponse<String> response) {
        List<String> preference = cookieParts(response, "display_currency");
        assertEquals("display_currency=eur", preference.getFirst());
        assertTrue(preference.contains("path=/"));
        assertTrue(preference.contains("samesite=lax"));
        assertTrue(preference.contains("secure"));
        assertFalse(preference.contains("httponly"));
        assertFalse(preference.contains("samesite=none"));
    }
}
