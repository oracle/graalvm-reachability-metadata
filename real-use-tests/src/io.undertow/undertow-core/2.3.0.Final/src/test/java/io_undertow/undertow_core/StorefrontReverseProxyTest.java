/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reverse proxying with `ProxyHandler` over a `LoadBalancingProxyClient`: round-robin balancing
 * across backend hosts, session affinity from the `JSESSIONID` route suffix, prefix-to-target path
 * rewriting, `X-Forwarded-*` and host-header rewriting, request headers added or stripped from
 * exchange attributes, failover away from an unreachable host, and a 503 once no host is left.
 *
 * A storefront edge server publishes `/shop/**` and forwards it to two catalog nodes, each an
 * embedded Undertow server mounted under `/catalog` and registered with its own jvmRoute. Each
 * node echoes what it received so the test can read the forwarded request back, and keeps its
 * shopping carts in node-local memory, so a cart only stays whole if every request of a session
 * reaches the node that issued its cookie. All servers bind ephemeral ports on the loopback.
 */
public class StorefrontReverseProxyTest {

    private static final HttpString EDGE = new HttpString("X-Edge");
    private static final HttpString EDGE_REQUEST = new HttpString("X-Edge-Request");
    private static final HttpString INTERNAL_DEBUG = new HttpString("X-Internal-Debug");

    private final List<Undertow> servers = new ArrayList<>();

    private CatalogNode nodeA;
    private CatalogNode nodeB;
    private LoadBalancingProxyClient balancer;
    private URI edge;
    private HttpClient client;

    @BeforeEach
    void startStorefront() {
        nodeA = new CatalogNode("node-a");
        nodeB = new CatalogNode("node-b");
        nodeA.authority = start(Handlers.path().addPrefixPath("/catalog", nodeA)).getAuthority();
        nodeB.authority = start(Handlers.path().addPrefixPath("/catalog", nodeB)).getAuthority();
        URI catalogA = URI.create("http://" + nodeA.authority + "/catalog");
        URI catalogB = URI.create("http://" + nodeB.authority + "/catalog");

        balancer = new LoadBalancingProxyClient()
                .addHost(catalogA, nodeA.route)
                .addHost(catalogB, nodeB.route)
                .setConnectionsPerThread(4);
        edge = startEdge(balancer);
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @AfterEach
    void stopServers() {
        servers.forEach(Undertow::stop);
    }

    @Test
    void rewritesThePublicPrefixAndAnnotatesTheForwardedRequest() throws Exception {
        HttpRequest request = request("/shop/items?sku=42&view=compact")
                .header("X-Forwarded-For", "203.0.113.7")
                .header("X-Internal-Debug", "trace-everything")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("text/plain; charset=UTF-8", response.headers().firstValue("Content-Type").orElseThrow());
        Map<String, String> seen = parse(response.body());
        CatalogNode served = seen.get("node").equals(nodeA.route) ? nodeA : nodeB;
        assertEquals("GET", seen.get("method"));
        assertEquals("/catalog/items", seen.get("path"));
        assertEquals("sku=42&view=compact", seen.get("query"));
        assertEquals(served.authority, seen.get("host"));
        assertEquals(edge.getAuthority(), seen.get("x-forwarded-host"));
        assertEquals("203.0.113.7,127.0.0.1", seen.get("x-forwarded-for"));
        assertEquals("http", seen.get("x-forwarded-proto"));
        assertEquals(String.valueOf(edge.getPort()), seen.get("x-forwarded-port"));
        assertEquals("storefront", seen.get("x-edge"));
        assertEquals("GET /shop/items", seen.get("x-edge-request"));
        assertEquals("<absent>", seen.get("x-internal-debug"));
    }

    @Test
    void balancesAnonymousTrafficRoundRobinAcrossTheCatalogNodes() throws Exception {
        List<String> nodes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            HttpResponse<String> response = send(request("/shop/ping").GET().build());
            assertEquals(200, response.statusCode());
            nodes.add(parse(response.body()).get("node"));
        }

        assertNotEquals(nodes.get(0), nodes.get(1));
        assertEquals(nodes.get(0), nodes.get(2));
        assertEquals(nodes.get(1), nodes.get(3));
        assertEquals(2, nodeA.hits.get());
        assertEquals(2, nodeB.hits.get());
    }

    @Test
    void keepsEveryCartRequestOnTheNodeThatIssuedTheSession() throws Exception {
        HttpResponse<String> created = send(request("/shop/cart")
                .POST(HttpRequest.BodyPublishers.ofString("sku-espresso"))
                .build());

        assertEquals(201, created.statusCode());
        String setCookie = created.headers().firstValue("Set-Cookie").orElseThrow();
        String session = setCookie.substring("JSESSIONID=".length(), setCookie.indexOf(';'));
        assertTrue(setCookie.startsWith("JSESSIONID=cart-1."), setCookie);
        CatalogNode owner = session.endsWith("." + nodeA.route) ? nodeA : nodeB;
        CatalogNode other = owner == nodeA ? nodeB : nodeA;
        assertEquals("/catalog/cart/" + session, created.headers().firstValue("Location").orElseThrow());

        for (String sku : List.of("sku-grinder", "sku-filters", "sku-mug")) {
            HttpResponse<String> added = send(request("/shop/cart")
                    .header("Cookie", "JSESSIONID=" + session)
                    .POST(HttpRequest.BodyPublishers.ofString(sku))
                    .build());
            assertEquals(201, added.statusCode());
            assertFalse(added.headers().firstValue("Set-Cookie").isPresent());
        }
        HttpResponse<String> cart = send(request("/shop/cart")
                .header("Cookie", "JSESSIONID=" + session)
                .GET()
                .build());

        assertEquals(200, cart.statusCode());
        assertEquals("""
                cart %s on %s
                sku-espresso
                sku-grinder
                sku-filters
                sku-mug
                """.formatted(session, owner.route), cart.body());
        assertEquals(5, owner.hits.get());
        assertEquals(0, other.hits.get());
        assertTrue(other.carts.isEmpty());
    }

    @Test
    void failsOverFromAnUnreachableNodeAndAnswers503WhenNoNodeIsLeft() throws Exception {
        URI liveCatalog = URI.create("http://" + nodeA.authority + "/catalog");
        URI deadCatalog = URI.create("http://127.0.0.1:" + unusedPort() + "/catalog");
        LoadBalancingProxyClient degraded = new LoadBalancingProxyClient()
                .addHost(liveCatalog, nodeA.route)
                .addHost(deadCatalog, "node-gone");
        edge = startEdge(degraded);

        HttpResponse<String> pinnedToDeadNode = send(request("/shop/ping")
                .header("Cookie", "JSESSIONID=cart-7.node-gone")
                .GET()
                .build());
        assertEquals(200, pinnedToDeadNode.statusCode());
        assertEquals(nodeA.route, parse(pinnedToDeadNode.body()).get("node"));

        for (int i = 0; i < 3; i++) {
            HttpResponse<String> anonymous = send(request("/shop/ping").GET().build());
            assertEquals(200, anonymous.statusCode());
            assertEquals(nodeA.route, parse(anonymous.body()).get("node"));
        }
        assertEquals(4, nodeA.hits.get());

        degraded.removeHost(liveCatalog);
        HttpResponse<String> unavailable = send(request("/shop/ping").GET().build());
        assertEquals(503, unavailable.statusCode());
        assertEquals(4, nodeA.hits.get());
    }

    @Test
    void answersPathsOutsideTheProxiedPrefixAtTheEdge() throws Exception {
        HttpResponse<String> health = send(request("/health").GET().build());
        assertEquals(200, health.statusCode());
        assertEquals("edge ok", health.body());

        HttpResponse<String> unknown = send(request("/admin").GET().build());
        assertEquals(404, unknown.statusCode());
        assertEquals(0, nodeA.hits.get() + nodeB.hits.get());
    }

    private URI startEdge(LoadBalancingProxyClient proxyClient) {
        HttpHandler proxy = ProxyHandler.builder()
                .setProxyClient(proxyClient)
                .setMaxRequestTime(10_000)
                .setRewriteHostHeader(true)
                .setReuseXForwarded(true)
                .addRequestHeader(EDGE, ExchangeAttributes.constant("storefront"))
                .addRequestHeader(EDGE_REQUEST, ExchangeAttributes.parser(getClass().getClassLoader()).parse("%m %U"))
                .addRequestHeader(INTERNAL_DEBUG, ExchangeAttributes.constant(""))
                .setNext(ResponseCodeHandler.HANDLE_404)
                .build();
        return start(Handlers.path(ResponseCodeHandler.HANDLE_404)
                .addPrefixPath("/shop", proxy)
                .addExactPath("/health", exchange -> text(exchange, StatusCodes.OK, "edge ok")));
    }

    private URI start(HttpHandler handler) {
        Undertow server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setIoThreads(2)
                .setHandler(handler)
                .build();
        server.start();
        servers.add(server);
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        return URI.create("http://127.0.0.1:" + address.getPort());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(edge.resolve(path)).timeout(Duration.ofSeconds(15));
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int unusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /// Reads the `key=value` lines a catalog node echoes back.
    private static Map<String, String> parse(String echo) {
        Map<String, String> fields = new HashMap<>();
        for (String line : echo.split("\n")) {
            int separator = line.indexOf('=');
            fields.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return fields;
    }

    private static void text(HttpServerExchange exchange, int status, String body) {
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
        exchange.getResponseSender().send(body, StandardCharsets.UTF_8);
    }

    /// A catalog backend: echoes forwarded requests and holds shopping carts in its own memory.
    private static final class CatalogNode implements HttpHandler {

        private final String route;
        private final AtomicInteger hits = new AtomicInteger();
        private final AtomicInteger sessions = new AtomicInteger();
        private final Map<String, List<String>> carts = new ConcurrentHashMap<>();
        private volatile String authority;

        CatalogNode(String route) {
            this.route = route;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) {
            hits.incrementAndGet();
            if (exchange.getRelativePath().equals("/cart")) {
                cart(exchange);
            } else {
                echo(exchange);
            }
        }

        private void echo(HttpServerExchange exchange) {
            StringBuilder body = new StringBuilder()
                    .append("node=").append(route).append('\n')
                    .append("method=").append(exchange.getRequestMethod()).append('\n')
                    .append("path=").append(exchange.getRequestPath()).append('\n')
                    .append("query=").append(exchange.getQueryString()).append('\n')
                    .append("host=").append(exchange.getRequestHeaders().getFirst(Headers.HOST)).append('\n');
            for (HttpString header : List.of(Headers.X_FORWARDED_HOST, Headers.X_FORWARDED_FOR,
                    Headers.X_FORWARDED_PROTO, Headers.X_FORWARDED_PORT, EDGE, EDGE_REQUEST, INTERNAL_DEBUG)) {
                String value = exchange.getRequestHeaders().getFirst(header);
                body.append(header.toString().toLowerCase()).append('=')
                        .append(value == null ? "<absent>" : value).append('\n');
            }
            text(exchange, StatusCodes.OK, body.toString().stripTrailing());
        }

        private void cart(HttpServerExchange exchange) {
            Cookie cookie = exchange.getRequestCookie("JSESSIONID");
            if (Methods.GET.equals(exchange.getRequestMethod())) {
                List<String> items = cookie == null ? List.of() : carts.getOrDefault(cookie.getValue(), List.of());
                String session = cookie == null ? "<none>" : cookie.getValue();
                StringBuilder body = new StringBuilder("cart " + session + " on " + route + "\n");
                items.forEach(item -> body.append(item).append('\n'));
                text(exchange, StatusCodes.OK, body.toString());
                return;
            }
            exchange.getRequestReceiver().receiveFullString((ex, sku) -> {
                String session;
                if (cookie == null || !carts.containsKey(cookie.getValue())) {
                    session = "cart-" + sessions.incrementAndGet() + "." + route;
                    carts.put(session, new CopyOnWriteArrayList<>());
                    ex.setResponseCookie(new CookieImpl("JSESSIONID", session).setPath("/"));
                } else {
                    session = cookie.getValue();
                }
                carts.get(session).add(sku);
                ex.getResponseHeaders().put(Headers.LOCATION, "/catalog/cart/" + session);
                text(ex, StatusCodes.CREATED, "added " + sku);
            }, StandardCharsets.UTF_8);
        }
    }
}
