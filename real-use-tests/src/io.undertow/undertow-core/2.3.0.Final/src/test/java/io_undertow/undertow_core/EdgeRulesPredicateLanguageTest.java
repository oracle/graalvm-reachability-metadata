/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.PredicatedHandler;
import io.undertow.server.handlers.builder.PredicatedHandlersParser;
import io.undertow.util.Headers;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The predicate and handler configuration language: rules in the textual
 * `predicate -> handler` syntax parsed by `PredicatedHandlersParser` and applied with
 * `Handlers.predicates`.
 *
 * A storefront keeps its edge rules in a text file instead of code, the way Undertow and WildFly
 * deployments use `undertow-handlers.conf`. The rules add a security header to every response,
 * answer health checks, redirect a retired catalog prefix, rewrite legacy product URLs with regex
 * capture groups, copy a path-template parameter into a response header, restrict the API to
 * certain methods, gate admin and metrics paths by client address, turn away scanner probes with
 * a block of handlers, and route beta tenants with an `else` branch. A rule naming a handler that
 * does not exist fails at parse time, as a misconfigured deployment would at startup.
 */
public class EdgeRulesPredicateLanguageTest {

    private static final String EDGE_RULES = """
            true -> header(header='X-Frame-Options', value='DENY')
            path('/health') -> response-code(204)
            path-prefix('/old-catalog') -> redirect('/catalog${remaining}')
            regex('^/products/([0-9]+)[.]html$') -> rewrite('/catalog/item/${1}')
            path-template('/api/{version}/orders') -> header(header='X-Api-Version', value='${version}')
            path-prefix('/api') -> allowed-methods(methods={GET, POST})
            path-prefix('/admin') -> ip-access-control(acl={'10.0.0.0/8 allow', '127.0.0.1 deny'})
            path-prefix('/metrics') -> ip-access-control(default-allow=false, acl={'127.0.0.0/8 allow'})
            path-suffix('.php') -> {
                header(header='X-Blocked', value='scanner')
                response-code(404)
            }
            equals(value={'%{i,X-Tenant}', 'beta'}) -> rewrite('/beta%{RELATIVE_PATH}') else header(header='X-Tenant', value='stable')
            """;

    private Undertow server;
    private HttpClient client;
    private URI base;

    @BeforeEach
    void startServer() {
        List<PredicatedHandler> rules = PredicatedHandlersParser.parse(
                EDGE_RULES, EdgeRulesPredicateLanguageTest.class.getClassLoader());
        HttpHandler edge = Handlers.predicates(rules, new Storefront());
        server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setHandler(edge)
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
    void parsesOneRulePerLineAndBlock() {
        assertEquals(10, PredicatedHandlersParser.parse(EDGE_RULES, getClass().getClassLoader()).size());
    }

    @Test
    void passesOrdinaryRequestsThroughWithTheHeadersRulesAdd() throws Exception {
        HttpResponse<String> home = send("GET", "/catalog", null);
        assertEquals(200, home.statusCode());
        assertEquals("path=/catalog", home.body());
        assertEquals("DENY", home.headers().firstValue("X-Frame-Options").orElseThrow());
        assertEquals("stable", home.headers().firstValue("X-Tenant").orElseThrow());

        HttpResponse<String> health = send("GET", "/health", null);
        assertEquals(204, health.statusCode());
        assertEquals("", health.body());
        assertEquals("DENY", health.headers().firstValue("X-Frame-Options").orElseThrow());
    }

    @Test
    void redirectsAndRewritesLegacyUrls() throws Exception {
        HttpResponse<String> retired = send("GET", "/old-catalog/shoes/running", null);
        assertEquals(302, retired.statusCode());
        assertTrue(retired.headers().firstValue("Location").orElseThrow().endsWith("/catalog/shoes/running"),
                retired.headers().firstValue("Location").orElseThrow());

        HttpResponse<String> legacyProduct = send("GET", "/products/4711.html", null);
        assertEquals(200, legacyProduct.statusCode());
        assertEquals("path=/catalog/item/4711", legacyProduct.body());

        HttpResponse<String> notLegacy = send("GET", "/products/sale.html", null);
        assertEquals("path=/products/sale.html", notLegacy.body());
    }

    @Test
    void exposesTemplateParametersAndRestrictsApiMethods() throws Exception {
        HttpResponse<String> orders = send("POST", "/api/v3/orders", "{}");
        assertEquals(200, orders.statusCode());
        assertEquals("path=/api/v3/orders", orders.body());
        assertEquals("v3", orders.headers().firstValue("X-Api-Version").orElseThrow());

        HttpResponse<String> deleted = send("DELETE", "/api/v3/orders", null);
        assertEquals(405, deleted.statusCode());

        HttpResponse<String> other = send("GET", "/api/v3/customers", null);
        assertEquals(200, other.statusCode());
        assertFalse(other.headers().firstValue("X-Api-Version").isPresent());
    }

    @Test
    void gatesOperationalPathsByClientAddress() throws Exception {
        HttpResponse<String> admin = send("GET", "/admin/users", null);
        assertEquals(403, admin.statusCode());
        assertFalse(admin.body().startsWith("path="));

        HttpResponse<String> metrics = send("GET", "/metrics", null);
        assertEquals(200, metrics.statusCode());
        assertEquals("path=/metrics", metrics.body());
    }

    @Test
    void runsEveryHandlerInABlockForScannerProbes() throws Exception {
        HttpResponse<String> probe = send("GET", "/wp-login.php", null);
        assertEquals(404, probe.statusCode());
        assertEquals("scanner", probe.headers().firstValue("X-Blocked").orElseThrow());
        assertFalse(probe.body().startsWith("path="));
    }

    @Test
    void routesBetaTenantsThroughTheElseBranch() throws Exception {
        HttpRequest beta = HttpRequest.newBuilder(base.resolve("/catalog/item/7"))
                .timeout(Duration.ofSeconds(10))
                .header("X-Tenant", "beta")
                .GET()
                .build();
        HttpResponse<String> response = client.send(beta, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("path=/beta/catalog/item/7", response.body());
        assertFalse(response.headers().firstValue("X-Tenant").isPresent());
    }

    @Test
    void rejectsRulesThatNameUnknownHandlers() {
        ClassLoader loader = getClass().getClassLoader();
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> PredicatedHandlersParser.parse("path('/x') -> launch-rockets(count=3)", loader));
        assertTrue(failure.getMessage().contains("launch-rockets"), failure.getMessage());
    }

    private HttpResponse<String> send(String method, String path, String body)
            throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .method(method, publisher)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /// The application behind the edge rules: it reports the path it was finally asked for.
    private static final class Storefront implements HttpHandler {

        @Override
        public void handleRequest(HttpServerExchange exchange) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send("path=" + exchange.getRelativePath());
        }
    }
}
