/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionActivationListener;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.net.CookieManager;
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
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps coffee-shop shopping carts in HTTP sessions managed by `StandardManager`, which issues
 * the configured session cookie, notifies `HttpSessionListener`, `HttpSessionAttributeListener`
 * and `HttpSessionIdListener`, and persists sessions to `SESSIONS.ser` across a server restart.
 *
 * A coffee roaster runs its web shop on embedded Tomcat. A visitor fills a cart before signing
 * in; the session cookie is named `CARTSESSION`, scoped to the shop and marked `HttpOnly` and
 * `SameSite=Strict`. Signing in rotates the session id to prevent fixation while keeping the
 * cart. When the shop is restarted for a deployment, the manager passivates the carts to the
 * work directory and the next server restores them, so the visitor's cookie still finds the
 * cart. Signing out invalidates the session and the audit listener records its destruction.
 */
class CoffeeShopSessionPersistenceTest {

    private static final String CART_ATTRIBUTE = "coffeeshop.cart";

    private static final String CUSTOMER_ATTRIBUTE = "coffeeshop.customer";

    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Path baseDir;

    private Shop shop;

    @BeforeEach
    void startShop() throws IOException, LifecycleException {
        baseDir = Files.createTempDirectory("coffeeshop-tomcat");
        shop = Shop.start(baseDir);
    }

    @AfterEach
    void stopShop() throws LifecycleException, IOException {
        shop.stop();
        try (var paths = Files.walk(baseDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void issuesTheConfiguredSessionCookieWhenTheCartIsFirstFilled() throws Exception {
        HttpResponse<String> empty = send(get("/cart"));
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(empty.body()).isEqualTo("no cart");
        assertThat(empty.headers().allValues("Set-Cookie")).isEmpty();

        HttpResponse<String> added = send(addItem("ethiopia-yirgacheffe", 2));

        assertThat(added.statusCode()).isEqualTo(201);
        String cookie = added.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(cookie).startsWith("CARTSESSION=" + sessionId(added.body()) + ";");
        assertThat(cookie).contains("Path=/shop", "HttpOnly", "SameSite=Strict");
        assertThat(shop.audit.events).containsExactly(
                "created:" + sessionId(added.body()),
                "added:" + CART_ATTRIBUTE);
        assertThat(shop.manager.getActiveSessions()).isEqualTo(1);
        HttpSession held = shop.manager.findSession(sessionId(added.body())).getSession();
        assertThat(held.getMaxInactiveInterval()).isEqualTo(45 * 60);

        HttpResponse<String> cart = send(get("/cart"));
        assertThat(cart.body()).endsWith("items=ethiopia-yirgacheffe x2 passivated=0 activated=0");
        assertThat(cart.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void rotatesTheSessionIdOnSignInAndKeepsTheCart() throws Exception {
        String anonymousId = sessionId(send(addItem("colombia-huila", 1)).body());

        HttpResponse<String> signedIn = send(post("/cart/sign-in", Map.of("customer", "Ana")));

        assertThat(signedIn.statusCode()).isEqualTo(200);
        String[] ids = signedIn.body().split("->");
        assertThat(ids[0]).isEqualTo(anonymousId);
        assertThat(ids[1]).isNotEqualTo(anonymousId);
        assertThat(signedIn.headers().firstValue("Set-Cookie"))
                .hasValueSatisfying(cookie -> assertThat(cookie).startsWith("CARTSESSION=" + ids[1] + ";"));
        assertThat(shop.audit.events).containsExactly(
                "created:" + anonymousId,
                "added:" + CART_ATTRIBUTE,
                "idChanged:" + anonymousId + "->" + ids[1],
                "added:" + CUSTOMER_ATTRIBUTE);
        assertThat(shop.manager.findSession(anonymousId)).isNull();

        HttpResponse<String> cart = send(get("/cart"));
        assertThat(cart.body()).isEqualTo("session=" + ids[1] + " customer=Ana"
                + " items=colombia-huila x1 passivated=0 activated=0");
    }

    @Test
    void restoresCartsFromTheWorkDirectoryAfterARestart() throws Exception {
        send(addItem("kenya-nyeri", 1));
        send(addItem("kenya-nyeri", 2));
        String sessionId = sessionId(send(addItem("burr-grinder", 1)).body());
        Path sessionStore = baseDir.resolve("work/Tomcat/localhost/shop/SESSIONS.ser");

        shop.stop();

        assertThat(sessionStore).isRegularFile();
        assertThat(shop.audit.events).doesNotContain("destroyed:" + sessionId);

        shop = Shop.start(baseDir);

        assertThat(sessionStore).doesNotExist();
        assertThat(shop.manager.getActiveSessions()).isEqualTo(1);
        HttpResponse<String> cart = send(get("/cart"));
        assertThat(cart.statusCode()).isEqualTo(200);
        assertThat(cart.body()).isEqualTo("session=" + sessionId + " customer=guest"
                + " items=burr-grinder x1, kenya-nyeri x3 passivated=1 activated=1");
        assertThat(cart.headers().allValues("Set-Cookie")).isEmpty();
        assertThat(shop.audit.events).isEmpty();
    }

    @Test
    void invalidatesTheSessionOnSignOut() throws Exception {
        String sessionId = sessionId(send(addItem("guatemala-antigua", 3)).body());

        HttpResponse<String> signedOut = send(post("/cart/sign-out", Map.of()));

        assertThat(signedOut.statusCode()).isEqualTo(204);
        assertThat(shop.audit.events).containsExactly(
                "created:" + sessionId,
                "added:" + CART_ATTRIBUTE,
                "destroyed:" + sessionId + " items=1",
                "removed:" + CART_ATTRIBUTE);
        assertThat(shop.manager.getActiveSessions()).isZero();
        assertThat(shop.manager.findSession(sessionId)).isNull();

        HttpResponse<String> cart = send(get("/cart"));
        assertThat(cart.body()).isEqualTo("no cart");
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(URI.create(shop.baseUrl + path)).GET();
    }

    private HttpRequest.Builder addItem(String sku, int quantity) {
        return post("/cart/items", Map.of("sku", sku, "quantity", Integer.toString(quantity)));
    }

    private HttpRequest.Builder post(String path, Map<String, String> form) {
        String body = new TreeMap<>(form).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        return HttpRequest.newBuilder(URI.create(shop.baseUrl + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private static String sessionId(String body) {
        assertThat(body).startsWith("session=");
        return body.substring("session=".length(), body.indexOf(' '));
    }

    /** One running instance of the shop, with the session manager and audit log it was started with. */
    static final class Shop {

        final Tomcat tomcat;

        final StandardManager manager;

        final SessionAudit audit;

        final String baseUrl;

        private Shop(Tomcat tomcat, StandardManager manager, SessionAudit audit) {
            this.tomcat = tomcat;
            this.manager = manager;
            this.audit = audit;
            this.baseUrl = "http://localhost:" + tomcat.getConnector().getLocalPort() + "/shop";
        }

        static Shop start(Path baseDir) throws LifecycleException {
            Tomcat tomcat = new Tomcat();
            tomcat.setBaseDir(baseDir.toString());
            tomcat.setPort(0);
            tomcat.getConnector();

            Context context = tomcat.addContext("/shop", baseDir.toString());
            StandardManager manager = new StandardManager();
            manager.setPathname("SESSIONS.ser");
            context.setManager(manager);
            Tomcat.addServlet(context, "cart", new CartServlet());
            context.addServletMappingDecoded("/cart/*", "cart");

            SessionAudit audit = new SessionAudit();
            ServletContainerInitializer sessionSetup = (classes, servletContext) -> {
                SessionCookieConfig cookie = servletContext.getSessionCookieConfig();
                cookie.setName("CARTSESSION");
                cookie.setHttpOnly(true);
                cookie.setAttribute("SameSite", "Strict");
                servletContext.setSessionTimeout(45);
                servletContext.addListener(audit);
            };
            context.addServletContainerInitializer(sessionSetup, null);

            tomcat.start();
            return new Shop(tomcat, manager, audit);
        }

        void stop() throws LifecycleException {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    /** Records the session lifecycle the shop's operations team audits. */
    static final class SessionAudit
            implements HttpSessionListener, HttpSessionAttributeListener, HttpSessionIdListener {

        final List<String> events = new CopyOnWriteArrayList<>();

        @Override
        public void sessionCreated(HttpSessionEvent event) {
            events.add("created:" + event.getSession().getId());
        }

        @Override
        public void sessionDestroyed(HttpSessionEvent event) {
            Cart cart = (Cart) event.getSession().getAttribute(CART_ATTRIBUTE);
            events.add("destroyed:" + event.getSession().getId() + " items=" + cart.lines.size());
        }

        @Override
        public void sessionIdChanged(HttpSessionEvent event, String oldSessionId) {
            events.add("idChanged:" + oldSessionId + "->" + event.getSession().getId());
        }

        @Override
        public void attributeAdded(HttpSessionBindingEvent event) {
            events.add("added:" + event.getName());
        }

        @Override
        public void attributeRemoved(HttpSessionBindingEvent event) {
            if (event.getName().equals(CART_ATTRIBUTE)) {
                events.add("removed:" + event.getName());
            }
        }
    }

    /** The visitor's cart; it counts how often the container passivated and reactivated it. */
    static final class Cart implements Serializable, HttpSessionActivationListener {

        @Serial
        private static final long serialVersionUID = 1L;

        private final TreeMap<String, Integer> lines = new TreeMap<>();

        private int passivated;

        private int activated;

        void add(String sku, int quantity) {
            lines.merge(sku, quantity, Integer::sum);
        }

        @Override
        public void sessionWillPassivate(HttpSessionEvent event) {
            passivated++;
        }

        @Override
        public void sessionDidActivate(HttpSessionEvent event) {
            activated++;
        }

        String describe() {
            String items = lines.entrySet().stream()
                    .map(line -> line.getKey() + " x" + line.getValue())
                    .collect(Collectors.joining(", "));
            return "items=" + items + " passivated=" + passivated + " activated=" + activated;
        }
    }

    /** Shows, fills, signs in and signs out the cart held in the visitor's session. */
    static final class CartServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute(CART_ATTRIBUTE) == null) {
                response.getWriter().write("no cart");
                return;
            }
            Cart cart = (Cart) session.getAttribute(CART_ATTRIBUTE);
            Object customer = session.getAttribute(CUSTOMER_ATTRIBUTE);
            response.getWriter().write("session=" + session.getId()
                    + " customer=" + (customer == null ? "guest" : customer) + " " + cart.describe());
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            request.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/plain");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            switch (String.valueOf(request.getPathInfo())) {
                case "/items" -> {
                    HttpSession session = request.getSession(true);
                    Cart cart = (Cart) session.getAttribute(CART_ATTRIBUTE);
                    if (cart == null) {
                        cart = new Cart();
                        session.setAttribute(CART_ATTRIBUTE, cart);
                    }
                    cart.add(request.getParameter("sku"), Integer.parseInt(request.getParameter("quantity")));
                    response.setStatus(HttpServletResponse.SC_CREATED);
                    response.getWriter().write("session=" + session.getId() + " " + cart.describe());
                }
                case "/sign-in" -> {
                    String previousId = request.getSession(false).getId();
                    String newId = request.changeSessionId();
                    request.getSession(false).setAttribute(CUSTOMER_ATTRIBUTE, request.getParameter("customer"));
                    response.getWriter().write(previousId + "->" + newId);
                }
                case "/sign-out" -> {
                    request.getSession(false).invalidate();
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }
                default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }
}
