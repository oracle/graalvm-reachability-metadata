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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.server.session.SessionListener;
import io.undertow.util.Headers;
import io.undertow.util.Sessions;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HTTP sessions with `SessionAttachmentHandler` over an `InMemorySessionManager`: cookie-tracked
 * sessions created on demand through `Sessions`, session attributes, session-id rotation on
 * login, invalidation on logout, `SessionListener` lifecycle events, manager statistics, and
 * eviction of the oldest session once the session cap is reached.
 *
 * A small shop keeps each visitor's cart in a server-side session identified by a `CART` cookie.
 * Browsing without a cart creates no session; adding an item creates one; logging in rotates the
 * session id while keeping the cart; logging out invalidates the session and clears the cookie.
 * Each visitor is a JDK `HttpClient` with its own `CookieManager`, so cookies travel exactly as a
 * browser would carry them.
 */
public class ShoppingCartSessionTest {

    private static final String COOKIE = "CART";
    private static final int MAX_SESSIONS = 2;

    private Undertow server;
    private InMemorySessionManager sessions;
    private SessionEvents events;
    private URI base;

    @BeforeEach
    void startServer() {
        sessions = new InMemorySessionManager("shop", MAX_SESSIONS, true);
        events = new SessionEvents();
        sessions.registerSessionListener(events);
        SessionCookieConfig cookieConfig = new SessionCookieConfig()
                .setCookieName(COOKIE)
                .setPath("/")
                .setHttpOnly(true);
        Shop shop = new Shop(cookieConfig);
        RoutingHandler routes = Handlers.routing()
                .get("/cart", shop::showCart)
                .post("/cart", shop::addItem)
                .post("/login", shop::login)
                .post("/logout", shop::logout)
                .get("/whoami", shop::whoAmI);
        server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setHandler(new SessionAttachmentHandler(routes, sessions, cookieConfig))
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        base = URI.create("http://127.0.0.1:" + address.getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void browsingWithoutACartCreatesNoSession() throws Exception {
        Visitor visitor = new Visitor();

        HttpResponse<String> cart = visitor.send("GET", "/cart");

        assertEquals(200, cart.statusCode());
        assertEquals("empty", cart.body());
        assertTrue(cart.headers().allValues("Set-Cookie").isEmpty());
        assertNull(visitor.sessionCookie());
        assertEquals(0, sessions.getCreatedSessionCount());
        assertTrue(sessions.getActiveSessions().isEmpty());
        assertTrue(events.snapshot().isEmpty());
    }

    @Test
    void cartItemsAccumulateInTheCookieTrackedSession() throws Exception {
        Visitor visitor = new Visitor();

        HttpResponse<String> first = visitor.send("POST", "/cart?item=kettle");
        assertEquals(200, first.statusCode());
        assertEquals("kettle", first.body());
        String setCookie = first.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(setCookie.startsWith(COOKIE + "="), setCookie);
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        HttpCookie cookie = visitor.sessionCookie();
        assertEquals("/", cookie.getPath());
        assertTrue(sessions.getActiveSessions().contains(cookie.getValue()));

        HttpResponse<String> second = visitor.send("POST", "/cart?item=teapot");
        assertEquals("kettle,teapot", second.body());
        assertTrue(second.headers().allValues("Set-Cookie").isEmpty());
        assertEquals("kettle,teapot", visitor.send("GET", "/cart").body());
        assertEquals(cookie.getValue(), visitor.sessionCookie().getValue());

        Session session = sessions.getSession(cookie.getValue());
        assertEquals(List.of("kettle", "teapot"), session.getAttribute("items"));
        assertEquals(1, sessions.getCreatedSessionCount());
        assertEquals(List.of(
                "created",
                "added items=[kettle]",
                "updated items=[kettle, teapot] was [kettle]"), events.snapshot());
    }

    @Test
    void separateVisitorsGetIsolatedCarts() throws Exception {
        Visitor alice = new Visitor();
        Visitor bob = new Visitor();

        alice.send("POST", "/cart?item=mug");
        bob.send("POST", "/cart?item=spoon");
        bob.send("POST", "/cart?item=fork");

        assertEquals("mug", alice.send("GET", "/cart").body());
        assertEquals("spoon,fork", bob.send("GET", "/cart").body());
        assertNotEquals(alice.sessionCookie().getValue(), bob.sessionCookie().getValue());
        assertEquals(2, sessions.getActiveSessionCount());
        assertEquals("empty", new Visitor().send("GET", "/cart").body());
    }

    @Test
    void loginRotatesTheSessionIdAndKeepsTheCart() throws Exception {
        Visitor visitor = new Visitor();
        visitor.send("POST", "/cart?item=blender");
        String anonymousId = visitor.sessionCookie().getValue();

        HttpResponse<String> login = visitor.send("POST", "/login?user=dana");

        assertEquals(200, login.statusCode());
        String rotatedId = visitor.sessionCookie().getValue();
        assertNotEquals(anonymousId, rotatedId);
        assertEquals("welcome dana", login.body());
        assertTrue(login.headers().firstValue("Set-Cookie").orElseThrow().contains(rotatedId));
        assertNull(sessions.getSession(anonymousId));
        assertEquals(List.of(rotatedId), List.copyOf(sessions.getActiveSessions()));

        assertEquals("dana", visitor.send("GET", "/whoami").body());
        assertEquals("blender", visitor.send("GET", "/cart").body());
        assertEquals(1, sessions.getCreatedSessionCount());
        assertEquals(List.of(
                "created",
                "added items=[blender]",
                "id changed from " + anonymousId,
                "added user=dana"), events.snapshot());
    }

    @Test
    void logoutInvalidatesTheSessionAndClearsTheCookie() throws Exception {
        Visitor visitor = new Visitor();
        visitor.send("POST", "/cart?item=toaster");
        visitor.send("POST", "/login?user=eli");
        String sessionId = visitor.sessionCookie().getValue();

        HttpResponse<String> logout = visitor.send("POST", "/logout");

        assertEquals(204, logout.statusCode());
        String cleared = logout.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(cleared.startsWith(COOKIE + "="), cleared);
        assertTrue(cleared.contains("Max-Age=0"), cleared);
        assertNull(visitor.sessionCookie());
        assertNull(sessions.getSession(sessionId));
        assertTrue(sessions.getActiveSessions().isEmpty());
        List<String> recorded = events.snapshot();
        assertEquals("destroyed INVALIDATED", recorded.get(recorded.size() - 1));

        assertEquals("empty", visitor.send("GET", "/cart").body());
        HttpResponse<String> anonymous = visitor.send("GET", "/whoami");
        assertEquals(401, anonymous.statusCode());
        assertEquals("anonymous", anonymous.body());

        HttpResponse<String> stale = visitor.sendWithCookie("GET", "/cart", sessionId);
        assertEquals("empty", stale.body());
        assertFalse(sessions.getActiveSessions().contains(sessionId));
    }

    @Test
    void theOldestCartIsEvictedAtTheSessionCap() throws Exception {
        Visitor first = new Visitor();
        Visitor second = new Visitor();
        Visitor third = new Visitor();
        first.send("POST", "/cart?item=pan");
        second.send("POST", "/cart?item=pot");
        String firstId = first.sessionCookie().getValue();
        assertEquals(MAX_SESSIONS, sessions.getActiveSessionCount());

        third.send("POST", "/cart?item=lid");

        assertEquals(MAX_SESSIONS, sessions.getActiveSessionCount());
        assertNull(sessions.getSession(firstId));
        assertEquals("pot", second.send("GET", "/cart").body());
        assertEquals("lid", third.send("GET", "/cart").body());
        assertEquals("empty", first.send("GET", "/cart").body());
        assertEquals(3, sessions.getCreatedSessionCount());
        assertEquals(MAX_SESSIONS, sessions.getHighestSessionCount());
        assertEquals(0, sessions.getRejectedSessions());
        assertTrue(events.snapshot().contains("destroyed TIMEOUT"), events.snapshot().toString());
    }

    /// The shop's request handlers, all reading and writing the visitor's session.
    private static final class Shop {

        private final SessionConfig sessionConfig;

        Shop(SessionConfig sessionConfig) {
            this.sessionConfig = sessionConfig;
        }

        void showCart(HttpServerExchange exchange) {
            Session session = Sessions.getSession(exchange);
            List<String> items = session == null ? null : items(session);
            reply(exchange, StatusCodes.OK, items == null ? "empty" : String.join(",", items));
        }

        void addItem(HttpServerExchange exchange) {
            String item = exchange.getQueryParameters().get("item").getFirst();
            Session session = Sessions.getOrCreateSession(exchange);
            List<String> current = items(session);
            List<String> updated = new ArrayList<>(current == null ? List.of() : current);
            updated.add(item);
            session.setAttribute("items", List.copyOf(updated));
            reply(exchange, StatusCodes.OK, String.join(",", updated));
        }

        void login(HttpServerExchange exchange) {
            String user = exchange.getQueryParameters().get("user").getFirst();
            Session session = Sessions.getOrCreateSession(exchange);
            session.changeSessionId(exchange, sessionConfig);
            session.setAttribute("user", user);
            reply(exchange, StatusCodes.OK, "welcome " + user);
        }

        void logout(HttpServerExchange exchange) {
            Session session = Sessions.getSession(exchange);
            if (session != null) {
                session.invalidate(exchange);
            }
            exchange.setStatusCode(StatusCodes.NO_CONTENT);
            exchange.endExchange();
        }

        void whoAmI(HttpServerExchange exchange) {
            Session session = Sessions.getSession(exchange);
            Object user = session == null ? null : session.getAttribute("user");
            if (user == null) {
                reply(exchange, StatusCodes.UNAUTHORIZED, "anonymous");
            } else {
                reply(exchange, StatusCodes.OK, (String) user);
            }
        }

        @SuppressWarnings("unchecked")
        private static List<String> items(Session session) {
            return (List<String>) session.getAttribute("items");
        }

        private static void reply(HttpServerExchange exchange, int status, String body) {
            exchange.setStatusCode(status);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send(body);
        }
    }

    /// Records session lifecycle callbacks in the order the manager delivers them.
    private static final class SessionEvents implements SessionListener {

        private final List<String> events = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void sessionCreated(Session session, HttpServerExchange exchange) {
            events.add("created");
        }

        @Override
        public void sessionDestroyed(Session session, HttpServerExchange exchange, SessionDestroyedReason reason) {
            events.add("destroyed " + reason);
        }

        @Override
        public void attributeAdded(Session session, String name, Object value) {
            events.add("added " + name + "=" + value);
        }

        @Override
        public void attributeUpdated(Session session, String name, Object newValue, Object oldValue) {
            events.add("updated " + name + "=" + newValue + " was " + oldValue);
        }

        @Override
        public void sessionIdChanged(Session session, String oldSessionId) {
            events.add("id changed from " + oldSessionId);
        }

        List<String> snapshot() {
            synchronized (events) {
                return List.copyOf(events);
            }
        }
    }

    /// One browser: an `HttpClient` with its own cookie jar.
    private final class Visitor {

        private final CookieManager cookies = new CookieManager();
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> send(String method, String path) throws IOException, InterruptedException {
            return client.send(request(method, path).build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> sendWithCookie(String method, String path, String sessionId)
                throws IOException, InterruptedException {
            HttpRequest request = request(method, path).header("Cookie", COOKIE + "=" + sessionId).build();
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpCookie sessionCookie() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equals(COOKIE))
                    .findFirst()
                    .orElse(null);
        }

        private HttpRequest.Builder request(String method, String path) {
            return HttpRequest.newBuilder(base.resolve(path))
                    .timeout(Duration.ofSeconds(10))
                    .method(method, HttpRequest.BodyPublishers.noBody());
        }
    }
}
