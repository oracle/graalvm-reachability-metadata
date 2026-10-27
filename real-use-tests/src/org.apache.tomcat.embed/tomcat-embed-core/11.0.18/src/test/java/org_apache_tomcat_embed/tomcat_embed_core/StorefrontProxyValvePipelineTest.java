/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.AccessLogValve;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.catalina.valves.rewrite.RewriteValve;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs a storefront behind a reverse proxy through the container valve pipeline, where an
 * engine `RemoteIpValve` restores the client address, scheme and host from `X-Forwarded-*`
 * headers, a context `RewriteValve` applies the `WEB-INF/rewrite.config` rules with `txt:` and
 * `int:` rewrite maps, and a host `AccessLogValve` records each exchange in a log file.
 *
 * A shoe shop moved to new product URLs and terminates TLS at its load balancer. Old catalogue
 * links answer `301` to the new pages through a legacy-ID text map, short links are rewritten
 * internally to upper-case SKUs, search paths become query strings that keep the caller's own
 * parameters, retired promotions answer `410` and a known scraper gets `403`. Checkout is only
 * served over HTTPS: a plain request is redirected to the public host the proxy announced, while
 * one the proxy marks as HTTPS reaches the servlet as a secure request on port 443. Only the
 * configured proxies are trusted to report the client address, and the access log shows the
 * real client address, the request line as the rewrite left it and the final status.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StorefrontProxyValvePipelineTest {

    private static final String PUBLIC_HOST = "shop.example.com";

    private static final String CHANNEL_ATTRIBUTE = "storefront.channel";

    private static final String LEGACY_PRODUCTS = """
            # legacy catalogue id -> current SKU
            1001 SKU-1001
            1002 SKU-1002
            """;

    private static final String REWRITE_RULES = """
            RewriteMap legacy txt:%s
            RewriteMap upper int:toupper

            # Tag every catalogue request with the sales channel
            RewriteRule ^/(products|p|search)/.*$ - [E=storefront.channel:web]

            # Keep a known scraper out and retire old promotions; a refusing rule needs a
            # substitution, as the valve only ends the request after rewriting it
            RewriteCond %%{HTTP:User-Agent} ^badbot/.*$ [NC]
            RewriteRule ^(.*)$ /blocked$1 [F]
            RewriteRule ^/promo/2019/(.*)$ /retired/$1 [G]

            # Old catalogue links move permanently to the new product pages
            RewriteRule ^/item/([0-9]+)$ /products/${legacy:$1|discontinued} [R=301,L]

            # Short links and search paths are served by the product servlet
            RewriteRule ^/p/([a-z0-9-]+)$ /products/${upper:$1} [L]
            RewriteRule ^/search/([a-z]+)$ /products?category=$1 [QSA,L]

            # Checkout only over HTTPS on the public host
            RewriteCond %%{HTTPS} !=on
            RewriteRule ^/checkout/(.*)$ https://%%{HTTP:X-Forwarded-Host}%%{CONTEXT_PATH}/checkout/$1 [R=302,L]
            """;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Tomcat tomcat;

    private Path baseDir;

    private Path accessLog;

    private String baseUrl;

    @BeforeAll
    void startServer() throws IOException, LifecycleException {
        baseDir = Files.createTempDirectory("storefront-tomcat");
        Path legacyMap = baseDir.resolve("legacy-products.txt");
        Files.writeString(legacyMap, LEGACY_PRODUCTS, StandardCharsets.UTF_8);
        Path docBase = Files.createDirectories(baseDir.resolve("webapps/store"));
        Path webInf = Files.createDirectories(docBase.resolve("WEB-INF"));
        Files.writeString(webInf.resolve("rewrite.config"),
                REWRITE_RULES.formatted(legacyMap.toAbsolutePath()), StandardCharsets.UTF_8);

        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        tomcat.getConnector();

        RemoteIpValve remoteIp = new RemoteIpValve();
        remoteIp.setRemoteIpHeader("X-Forwarded-For");
        remoteIp.setProtocolHeader("X-Forwarded-Proto");
        remoteIp.setHostHeader("X-Forwarded-Host");
        remoteIp.setTrustedProxies("198\\.51\\.100\\.2");
        tomcat.getEngine().getPipeline().addValve(remoteIp);

        Path logDir = baseDir.resolve("logs");
        AccessLogValve accessLogValve = new AccessLogValve();
        accessLogValve.setDirectory(logDir.toAbsolutePath().toString());
        accessLogValve.setPrefix("storefront_access");
        accessLogValve.setSuffix(".log");
        accessLogValve.setRotatable(false);
        accessLogValve.setBuffered(false);
        accessLogValve.setRequestAttributesEnabled(true);
        accessLogValve.setPattern("%a %{X-Request-Id}i \"%r\" %s");
        tomcat.getHost().getPipeline().addValve(accessLogValve);
        accessLog = logDir.resolve("storefront_access.log");

        Context context = tomcat.addContext("/store", docBase.toString());
        context.getPipeline().addValve(new RewriteValve());
        Tomcat.addServlet(context, "products", new ProductServlet());
        context.addServletMappingDecoded("/products/*", "products");
        Tomcat.addServlet(context, "checkout", new CheckoutServlet());
        context.addServletMappingDecoded("/checkout/*", "checkout");

        tomcat.start();
        baseUrl = "http://localhost:" + tomcat.getConnector().getLocalPort() + "/store";
    }

    @AfterAll
    void stopServer() throws LifecycleException, IOException {
        tomcat.stop();
        tomcat.destroy();
        try (var paths = Files.walk(baseDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void redirectsLegacyCatalogueLinksThroughTheTextMap() throws Exception {
        HttpResponse<String> known = send(get("/item/1002"));
        HttpResponse<String> retired = send(get("/item/7777"));

        assertThat(known.statusCode()).isEqualTo(301);
        assertThat(known.headers().firstValue("Location")).hasValue("/store/products/SKU-1002");
        assertThat(retired.statusCode()).isEqualTo(301);
        assertThat(retired.headers().firstValue("Location")).hasValue("/store/products/discontinued");

        HttpResponse<String> followed = send(get("/products/SKU-1002"));
        assertThat(followed.statusCode()).isEqualTo(200);
        assertThat(followed.body()).contains("product=SKU-1002 name=Trail Runner GTX");
    }

    @Test
    void rewritesShortLinksInternallyToUpperCaseSkus() throws Exception {
        HttpResponse<String> response = send(get("/p/sku-1001"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.body()).isEqualTo("""
                servlet=/products path=/SKU-1001 channel=web
                product=SKU-1001 name=Canvas Sneaker
                """);
    }

    @Test
    void turnsSearchPathsIntoQueriesThatKeepTheCallersParameters() throws Exception {
        HttpResponse<String> response = send(get("/search/boots?sort=price"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("""
                servlet=/products path=null channel=web
                category=boots sort=price
                """);
    }

    @Test
    void refusesTheScraperAndRetiredPromotions() throws Exception {
        HttpResponse<String> scraper = send(get("/products/SKU-1001").header("User-Agent", "BadBot/2.1"));
        HttpResponse<String> promotion = send(get("/promo/2019/summer-sale"));

        assertThat(scraper.statusCode()).isEqualTo(403);
        assertThat(promotion.statusCode()).isEqualTo(410);
    }

    @Test
    void redirectsPlainCheckoutToHttpsOnThePublicHost() throws Exception {
        HttpResponse<String> response = send(get("/checkout/cart?coupon=FALL24")
                .header("X-Forwarded-For", "203.0.113.7")
                .header("X-Forwarded-Host", PUBLIC_HOST));

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location"))
                .hasValue("https://" + PUBLIC_HOST + "/store/checkout/cart?coupon=FALL24");
    }

    @Test
    void servesCheckoutForwardedAsHttpsAsASecureRequest() throws Exception {
        HttpResponse<String> response = send(get("/checkout/cart")
                .header("X-Forwarded-For", "203.0.113.7")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", PUBLIC_HOST));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("""
                client=203.0.113.7 scheme=https secure=true server=shop.example.com:443
                forwardedFor=null forwardedBy=null
                """);
    }

    @Test
    void trustsOnlyTheConfiguredProxiesToReportTheClient() throws Exception {
        HttpResponse<String> viaTrustedProxy = send(get("/checkout/cart")
                .header("X-Forwarded-For", "203.0.113.7, 198.51.100.2")
                .header("X-Forwarded-Proto", "https"));
        HttpResponse<String> viaUnknownProxy = send(get("/checkout/cart")
                .header("X-Forwarded-For", "203.0.113.7, 192.0.2.66")
                .header("X-Forwarded-Proto", "https"));

        assertThat(viaTrustedProxy.body()).startsWith("client=203.0.113.7 ")
                .endsWith("forwardedFor=null forwardedBy=198.51.100.2\n");
        assertThat(viaUnknownProxy.body()).startsWith("client=192.0.2.66 ")
                .endsWith("forwardedFor=203.0.113.7 forwardedBy=null\n");
    }

    @Test
    void logsTheClientAddressRewrittenRequestLineAndFinalStatus() throws Exception {
        send(get("/p/sku-1001")
                .header("X-Forwarded-For", "203.0.113.50")
                .header("X-Request-Id", "req-short-link"));
        send(get("/item/1001")
                .header("X-Forwarded-For", "203.0.113.51")
                .header("X-Request-Id", "req-legacy"));
        send(get("/promo/2019/summer-sale")
                .header("X-Forwarded-For", "203.0.113.52")
                .header("X-Request-Id", "req-promo"));

        List<String> lines = awaitLogLines(List.of("req-short-link", "req-legacy", "req-promo"));

        assertThat(lines).contains(
                "203.0.113.50 req-short-link \"GET /store/products/SKU-1001 HTTP/1.1\" 200",
                "203.0.113.51 req-legacy \"GET /store/item/1001 HTTP/1.1\" 301",
                "203.0.113.52 req-promo \"GET /store/promo/2019/summer-sale HTTP/1.1\" 410");
    }

    /** Waits until the access log holds a line for every request id; logging follows the response. */
    private List<String> awaitLogLines(List<String> requestIds) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (true) {
            List<String> lines = Files.exists(accessLog)
                    ? Files.readAllLines(accessLog, StandardCharsets.UTF_8)
                    : List.of();
            boolean complete = requestIds.stream()
                    .allMatch(id -> lines.stream().anyMatch(line -> line.contains(" " + id + " ")));
            if (complete || System.nanoTime() > deadline) {
                return lines;
            }
            Thread.sleep(20);
        }
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** Shows one product by SKU, or the products of the requested category. */
    static final class ProductServlet extends HttpServlet {

        private final Map<String, String> products = Map.of(
                "SKU-1001", "Canvas Sneaker",
                "SKU-1002", "Trail Runner GTX");

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            String pathInfo = request.getPathInfo();
            String sku = pathInfo == null ? null : pathInfo.substring(1);
            if (sku != null && !products.containsKey(sku)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.setContentType("text/plain");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            PrintWriter writer = response.getWriter();
            writer.print("servlet=" + request.getServletPath() + " path=" + pathInfo
                    + " channel=" + request.getAttribute(CHANNEL_ATTRIBUTE) + "\n");
            if (sku == null) {
                writer.print("category=" + request.getParameter("category")
                        + " sort=" + request.getParameter("sort") + "\n");
            } else {
                writer.print("product=" + sku + " name=" + products.get(sku) + "\n");
            }
        }
    }

    /** Reports how the container sees the shopper behind the proxy at checkout. */
    static final class CheckoutServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            PrintWriter writer = response.getWriter();
            writer.print("client=" + request.getRemoteAddr() + " scheme=" + request.getScheme()
                    + " secure=" + request.isSecure()
                    + " server=" + request.getServerName() + ":" + request.getServerPort() + "\n");
            writer.print("forwardedFor=" + Objects.toString(request.getHeader("X-Forwarded-For"))
                    + " forwardedBy=" + Objects.toString(request.getHeader("X-Forwarded-By")) + "\n");
        }
    }
}
