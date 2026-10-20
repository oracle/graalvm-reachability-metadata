/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises HttpClient's protocol interceptor subsystem to attach tenant credentials and request IDs to reports.
public class TenantReportInterceptorsTest {
    @Test
    @Timeout(60)
    public void appliesEachRequestsTenantContextWithoutLeakingAnotherTenantsCredentials() throws IOException {
        Map<String, String> credentials = Map.of("north", "north-report-key", "south", "south-report-key");
        Map<String, String> reports = Map.of(
                "north", "warehouse,available\nnorth,18\n",
                "south", "warehouse,available\nsouth,7\n");
        List<String> receivedRequests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/reports/stock", exchange -> {
            String tenant = exchange.getRequestHeaders().getFirst("X-Tenant");
            String apiKey = exchange.getRequestHeaders().getFirst("X-Api-Key");
            String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
            if (tenant == null || !credentials.containsKey(tenant)
                    || !credentials.get(tenant).equals(apiKey) || requestId == null
                    || !"GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 403, "Tenant credentials required");
                return;
            }
            receivedRequests.add(tenant + " " + requestId);
            exchange.getResponseHeaders().set("X-Request-Id", requestId);
            respond(exchange, 200, reports.get(tenant));
        });
        server.start();

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(10_000)
                .setConnectionRequestTimeout(10_000)
                .build();
        HttpRequestInterceptor tenantHeaders = (request, context) -> {
            String tenant = (String) context.getAttribute("application.tenant");
            request.setHeader("X-Tenant", tenant);
            request.setHeader("X-Api-Key", credentials.get(tenant));
            request.setHeader("X-Request-Id", (String) context.getAttribute("application.requestId"));
        };
        String reportUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/reports/stock";
        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .addInterceptorLast(tenantHeaders)
                .build()) {
            downloadReport(client, reportUrl, "north", "report-101", reports.get("north"));
            downloadReport(client, reportUrl, "south", "report-102", reports.get("south"));
            downloadReport(client, reportUrl, "north", "report-103", reports.get("north"));
            assertEquals(List.of("north report-101", "south report-102", "north report-103"), receivedRequests);
        } finally {
            server.stop(0);
        }
    }

    private static void downloadReport(CloseableHttpClient client, String url, String tenant,
                                       String requestId, String expectedReport) throws IOException {
        HttpClientContext context = HttpClientContext.create();
        context.setAttribute("application.tenant", tenant);
        context.setAttribute("application.requestId", requestId);
        try (CloseableHttpResponse response = client.execute(new HttpGet(url), context)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals(requestId, response.getFirstHeader("X-Request-Id").getValue());
            assertEquals(expectedReport, EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
