/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises RESTEasy's automatic cookie management subsystem for a path-scoped portal session.
public class PortalCookieSessionTest {

    @Test
    @Timeout(60)
    public void reusesTheLoginCookieOnlyWithinThePortalAndRemovesItOnLogout() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new PortalResource());
        server.start();
        try {
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);
            builder.enableCookieManagement();
            try (ResteasyClient client = builder.build()) {
                ResteasyWebTarget application = client.target("http://127.0.0.1:" + server.getAddress().getPort());
                ResteasyWebTarget portal = application.path("portal");
                assertSignedOut(portal);

                try (Response response = portal.path("login").request(MediaType.TEXT_PLAIN_TYPE)
                        .post(Entity.text("clerk:warehouse-password"))) {
                    assertEquals(200, response.getStatus());
                    assertEquals("Signed in as clerk", response.readEntity(String.class));
                    NewCookie session = response.getCookies().get("PORTAL_SESSION");
                    assertNotNull(session);
                    assertEquals("session-42", session.getValue());
                    assertEquals("/portal", session.getPath());
                    assertTrue(session.isHttpOnly());
                }

                // No Cookie header is supplied by application code: the client keeps the session.
                try (Response response = portal.path("profile").request(MediaType.TEXT_PLAIN_TYPE).get()) {
                    assertEquals(200, response.getStatus());
                    assertEquals("clerk: warehouse=north, role=dispatcher", response.readEntity(String.class));
                }
                try (Response response = application.path("health").request(MediaType.TEXT_PLAIN_TYPE).get()) {
                    assertEquals(200, response.getStatus());
                    assertEquals("Healthy; no portal credentials received", response.readEntity(String.class));
                }
                try (Response response = portal.path("logout").request().post(Entity.text(""))) {
                    assertEquals(204, response.getStatus());
                    assertEquals(0, response.getCookies().get("PORTAL_SESSION").getMaxAge());
                }

                // Max-Age=0 and an Expires date in the past delete the cookie without an expiration wait.
                assertSignedOut(portal);
            }
        } finally {
            server.stop(0);
        }
    }

    private static void assertSignedOut(ResteasyWebTarget portal) {
        try (Response response = portal.path("profile").request(MediaType.TEXT_PLAIN_TYPE).get()) {
            String body = response.readEntity(String.class);
            assertEquals(401, response.getStatus(), body);
            assertEquals("Sign in required; no session cookie received", body);
        }
    }

    private static final class PortalResource implements HttpHandler {
        private final AtomicBoolean activeSession = new AtomicBoolean();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String cookie = exchange.getRequestHeaders().getFirst(HttpHeaders.COOKIE);
                boolean authenticated = "PORTAL_SESSION=session-42".equals(cookie) && activeSession.get();
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();
                if ("/portal/login".equals(path) && "POST".equals(method)) {
                    String credentials = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    if (!"clerk:warehouse-password".equals(credentials)) {
                        respond(exchange, 401, "Invalid credentials");
                        return;
                    }
                    activeSession.set(true);
                    exchange.getResponseHeaders().add(HttpHeaders.SET_COOKIE,
                            "PORTAL_SESSION=session-42; Path=/portal; HttpOnly");
                    respond(exchange, 200, "Signed in as clerk");
                } else if ("/portal/profile".equals(path) && "GET".equals(method)) {
                    if (cookie == null) {
                        respond(exchange, 401, "Sign in required; no session cookie received");
                    } else if (authenticated) {
                        respond(exchange, 200, "clerk: warehouse=north, role=dispatcher");
                    } else {
                        respond(exchange, 403, "Invalid session cookie received: " + cookie);
                    }
                } else if ("/portal/logout".equals(path) && "POST".equals(method)) {
                    exchange.getRequestBody().readAllBytes();
                    if (!authenticated) {
                        respond(exchange, 401, "An active session is required to sign out");
                        return;
                    }
                    activeSession.set(false);
                    exchange.getResponseHeaders().add(HttpHeaders.SET_COOKIE,
                            "PORTAL_SESSION=deleted; Path=/portal; Max-Age=0; "
                                    + "Expires=Thu, 01-Jan-1970 00:00:00 GMT; HttpOnly");
                    exchange.sendResponseHeaders(204, -1);
                } else if ("/health".equals(path) && "GET".equals(method)) {
                    respond(exchange, cookie == null ? 200 : 400, cookie == null
                            ? "Healthy; no portal credentials received" : "Portal credentials leaked outside their path");
                } else {
                    respond(exchange, 404, "Unknown resource");
                }
            }
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
