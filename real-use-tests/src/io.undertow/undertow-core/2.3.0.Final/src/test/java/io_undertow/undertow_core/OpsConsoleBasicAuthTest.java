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
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.api.SecurityNotification;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.NotificationReceiverHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HTTP authentication with Undertow's security handlers: `BasicAuthenticationMechanism` checking
 * credentials against an application `IdentityManager`, constraint-driven authentication via
 * `AuthenticationConstraintHandler` and `AuthenticationCallHandler`, the authenticated `Account`
 * read from the exchange's `SecurityContext`, and audit events from `NotificationReceiverHandler`.
 *
 * An operations console runs on an embedded Undertow server bound to an ephemeral port. Its status
 * page is public, its reports need any signed-in operator, and its maintenance switch also needs
 * the `admin` role, which the handler checks itself and answers 403 when missing. Missing or wrong
 * credentials get a 401 with a `Basic` challenge for the console's realm. Every authentication
 * outcome is appended to an audit trail the test reads back after the response arrives.
 */
public class OpsConsoleBasicAuthTest {

    private static final String REALM = "Ops Console";

    private final ConcurrentLinkedQueue<String> audit = new ConcurrentLinkedQueue<>();

    private Undertow server;
    private HttpClient client;
    private URI base;

    @BeforeEach
    void startServer() {
        OperatorDirectory directory = new OperatorDirectory(Map.of(
                "alice", new Operator("alice", "s3cret-alice", Set.of("operator", "admin")),
                "bob", new Operator("bob", "hunter2", Set.of("operator"))));

        HttpHandler routes = Handlers.path()
                .addExactPath("/status", OpsConsoleBasicAuthTest::status)
                .addExactPath("/reports", protect(OpsConsoleBasicAuthTest::reports))
                .addExactPath("/maintenance", protect(OpsConsoleBasicAuthTest::maintenance));

        List<AuthenticationMechanism> mechanisms = List.of(new BasicAuthenticationMechanism(REALM));
        HttpHandler secured = new SecurityInitialHandler(
                AuthenticationMode.CONSTRAINT_DRIVEN,
                directory,
                new NotificationReceiverHandler(
                        new AuthenticationMechanismsHandler(routes, mechanisms),
                        List.of(this::record)));

        server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setHandler(secured)
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
    void servesThePublicStatusPageWithoutAskingForCredentials() throws Exception {
        HttpResponse<String> response = get("/status", null);

        assertEquals(200, response.statusCode());
        assertEquals("console up; signed in as: nobody", response.body());
        assertFalse(response.headers().firstValue("WWW-Authenticate").isPresent());
        assertTrue(audit.isEmpty(), "no authentication should run on an unconstrained path");
    }

    @Test
    void challengesAnonymousAndWrongCredentialRequestsForProtectedPages() throws Exception {
        HttpResponse<String> anonymous = get("/reports", null);
        assertEquals(401, anonymous.statusCode());
        assertEquals("Basic realm=\"" + REALM + "\"",
                anonymous.headers().firstValue("WWW-Authenticate").orElseThrow());
        assertTrue(audit.isEmpty(), "an absent Authorization header is not a failed login");

        HttpResponse<String> wrongPassword = get("/reports", basic("bob", "letmein"));
        assertEquals(401, wrongPassword.statusCode());
        assertTrue(wrongPassword.headers().firstValue("WWW-Authenticate").isPresent());

        HttpResponse<String> unknownUser = get("/reports", basic("mallory", "hunter2"));
        assertEquals(401, unknownUser.statusCode());

        assertEquals(List.of("FAILED_AUTHENTICATION", "FAILED_AUTHENTICATION"), List.copyOf(audit));
    }

    @Test
    void exposesTheAuthenticatedAccountToProtectedHandlers() throws Exception {
        HttpResponse<String> reports = get("/reports", basic("bob", "hunter2"));

        assertEquals(200, reports.statusCode());
        assertEquals("text/plain; charset=UTF-8", reports.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("""
                operator: bob
                mechanism: BASIC
                roles: [operator]
                """, reports.body());
        assertEquals(List.of("AUTHENTICATED bob via BASIC"), List.copyOf(audit));
    }

    @Test
    void enforcesTheAdminRoleInsideTheApplicationHandler() throws Exception {
        HttpResponse<String> forbidden = get("/maintenance", basic("bob", "hunter2"));
        assertEquals(403, forbidden.statusCode());
        assertEquals("bob lacks the admin role", forbidden.body());

        HttpResponse<String> allowed = get("/maintenance", basic("alice", "s3cret-alice"));
        assertEquals(200, allowed.statusCode());
        assertEquals("maintenance mode enabled by alice", allowed.body());

        assertEquals(List.of("AUTHENTICATED bob via BASIC", "AUTHENTICATED alice via BASIC"),
                List.copyOf(audit));
    }

    @Test
    void skipsAuthenticationOnPublicPagesEvenWhenCredentialsAreSent() throws Exception {
        HttpResponse<String> response = get("/status", basic("alice", "s3cret-alice"));

        assertEquals(200, response.statusCode());
        assertEquals("console up; signed in as: nobody", response.body());
        assertTrue(audit.isEmpty(), "constraint-driven mode skips authentication on public paths");
    }

    private HttpResponse<String> get(String path, String authorization)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .GET();
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String basic(String user, String password) {
        String token = user + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.ISO_8859_1));
    }

    /// Requires an authenticated caller before `next` runs, challenging the client otherwise.
    private static HttpHandler protect(HttpHandler next) {
        return new AuthenticationConstraintHandler(new AuthenticationCallHandler(next));
    }

    private void record(SecurityNotification notification) {
        if (notification.getEventType() == SecurityNotification.EventType.AUTHENTICATED) {
            audit.add("AUTHENTICATED " + notification.getAccount().getPrincipal().getName()
                    + " via " + notification.getMechanism());
        } else {
            audit.add(notification.getEventType().name());
        }
    }

    private static void status(HttpServerExchange exchange) {
        Account account = exchange.getSecurityContext().getAuthenticatedAccount();
        String who = account == null ? "nobody" : account.getPrincipal().getName();
        text(exchange, StatusCodes.OK, "console up; signed in as: " + who);
    }

    private static void reports(HttpServerExchange exchange) {
        SecurityContext security = exchange.getSecurityContext();
        Account account = security.getAuthenticatedAccount();
        text(exchange, StatusCodes.OK, "operator: " + account.getPrincipal().getName() + "\n"
                + "mechanism: " + security.getMechanismName() + "\n"
                + "roles: " + account.getRoles().stream().sorted().toList() + "\n");
    }

    private static void maintenance(HttpServerExchange exchange) {
        Account account = exchange.getSecurityContext().getAuthenticatedAccount();
        String name = account.getPrincipal().getName();
        if (!account.getRoles().contains("admin")) {
            text(exchange, StatusCodes.FORBIDDEN, name + " lacks the admin role");
            return;
        }
        text(exchange, StatusCodes.OK, "maintenance mode enabled by " + name);
    }

    private static void text(HttpServerExchange exchange, int status, String body) {
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
        exchange.getResponseSender().send(body, StandardCharsets.UTF_8);
    }

    /// A console operator with the password the directory checks and the roles it grants.
    private record Operator(String name, String password, Set<String> roles) implements Account {

        @Override
        public Principal getPrincipal() {
            return () -> name;
        }

        @Override
        public Set<String> getRoles() {
            return roles;
        }
    }

    /// The application's user store, consulted by the Basic mechanism for every login attempt.
    private record OperatorDirectory(Map<String, Operator> operators) implements IdentityManager {

        @Override
        public Account verify(Account account) {
            return operators.get(account.getPrincipal().getName());
        }

        @Override
        public Account verify(String id, Credential credential) {
            Operator operator = operators.get(id);
            if (operator == null || !(credential instanceof PasswordCredential password)) {
                return null;
            }
            boolean matches = Arrays.equals(operator.password().toCharArray(), password.getPassword());
            return matches ? operator : null;
        }

        @Override
        public Account verify(Credential credential) {
            return null;
        }
    }
}
