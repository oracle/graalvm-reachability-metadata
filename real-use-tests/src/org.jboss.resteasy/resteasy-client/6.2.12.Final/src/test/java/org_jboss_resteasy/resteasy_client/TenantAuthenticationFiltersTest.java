/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises RESTEasy's client filter pipeline for tenant authentication and response auditing.
public class TenantAuthenticationFiltersTest {
    private static final String CREDENTIALS = "application.credentials";
    private static final String TENANT = "X-Tenant";
    private static final String REQUEST_ID = "X-Request-Id";

    @Test
    @Timeout(60)
    public void authenticatesEachInvoiceRequestAndAuditsSuccessAndDenial() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/invoices", TenantAuthenticationFiltersTest::serveInvoices);
        server.start();
        try {
            List<AuditEntry> audit = new ArrayList<>();
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);
            try (ResteasyClient client = builder.build()) {
                ResteasyWebTarget invoices = client.target("http://127.0.0.1:" + server.getAddress().getPort())
                        .path("invoices");
                invoices.register(new TenantAuthenticationFilter());
                invoices.register(new ResponseAuditFilter(audit));

                try (Response response = invoices.request(MediaType.TEXT_PLAIN_TYPE)
                        .property(CREDENTIALS, new Credentials("north", "north-token", "invoice-allowed"))
                        .get()) {
                    assertEquals(200, response.getStatus());
                    assertEquals("north: invoice-42, total=125.00", response.readEntity(String.class));
                    assertEquals("invoice-allowed", response.getHeaderString(REQUEST_ID));
                }

                try (Response response = invoices.request(MediaType.TEXT_PLAIN_TYPE)
                        .property(CREDENTIALS, new Credentials("south", "north-token", "invoice-denied"))
                        .get()) {
                    assertEquals(403, response.getStatus());
                    assertEquals("Token does not grant access to tenant south", response.readEntity(String.class));
                    assertEquals("invoice-denied", response.getHeaderString(REQUEST_ID));
                }

                assertEquals(List.of(
                        new AuditEntry("north", "invoice-allowed", 200),
                        new AuditEntry("south", "invoice-denied", 403)), audit);
            }
        } finally {
            server.stop(0);
        }
    }

    private static void serveInvoices(HttpExchange exchange) throws IOException {
        try (exchange) {
            String tenant = exchange.getRequestHeaders().getFirst(TENANT);
            String authorization = exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            String requestId = exchange.getRequestHeaders().getFirst(REQUEST_ID);
            int status;
            String body;
            if (!"Bearer north-token".equals(authorization)) {
                status = 401;
                body = "A valid bearer token is required";
                exchange.getResponseHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"invoices\"");
            } else if (!"north".equals(tenant)) {
                status = 403;
                body = "Token does not grant access to tenant " + tenant;
            } else {
                status = 200;
                body = "north: invoice-42, total=125.00";
            }
            if (requestId != null) {
                exchange.getResponseHeaders().set(REQUEST_ID, requestId);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private record Credentials(String tenant, String token, String requestId) {
    }

    private record AuditEntry(String tenant, String requestId, int status) {
    }

    public static final class TenantAuthenticationFilter implements ClientRequestFilter {
        @Override
        public void filter(ClientRequestContext request) {
            Credentials credentials = (Credentials) request.getProperty(CREDENTIALS);
            request.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, "Bearer " + credentials.token());
            request.getHeaders().putSingle(TENANT, credentials.tenant());
            request.getHeaders().putSingle(REQUEST_ID, credentials.requestId());
        }
    }

    public static final class ResponseAuditFilter implements ClientResponseFilter {
        private final List<AuditEntry> audit;

        private ResponseAuditFilter(List<AuditEntry> audit) {
            this.audit = audit;
        }

        @Override
        public void filter(ClientRequestContext request, ClientResponseContext response) {
            audit.add(new AuditEntry(request.getHeaderString(TENANT),
                    response.getHeaders().getFirst(REQUEST_ID), response.getStatus()));
        }
    }
}
