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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises RESTEasy's HTTP redirect subsystem to download an exported sales report.
public class RedirectedReportDownloadTest {
    private static final String REPORT = """
            invoice,total
            invoice-42,125.00
            invoice-43,75.50
            """;
    private static final String DOWNLOAD = "files/monthly.csv?token=report%2B42";

    @Test
    @Timeout(60)
    public void inspectsAnExportLocationThenDownloadsWithAutomaticRedirects() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/reports/monthly", RedirectedReportDownloadTest::redirectToReport);
        server.createContext("/reports/files/monthly.csv", RedirectedReportDownloadTest::downloadReport);
        server.start();
        try {
            URI exportUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/reports/monthly");
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);
            builder.setFollowRedirects(false);
            try (ResteasyClient client = builder.build();
                    Response response = client.target(exportUri).request("text/csv").get()) {
                assertEquals(303, response.getStatus());
                assertEquals(exportUri.resolve(DOWNLOAD), exportUri.resolve(response.getLocation()));
            }

            builder.setFollowRedirects(true);
            try (ResteasyClient client = builder.build();
                    Response response = client.target(exportUri).request("text/csv").get()) {
                assertEquals(200, response.getStatus());
                assertEquals("text/csv", response.getMediaType().toString());
                assertEquals("attachment; filename=monthly.csv", response.getHeaderString("Content-Disposition"));
                assertEquals(REPORT, response.readEntity(String.class));
            }
        } finally {
            server.stop(0);
        }
    }

    private static void redirectToReport(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set(HttpHeaders.LOCATION, DOWNLOAD);
            exchange.sendResponseHeaders(303, -1);
        }
    }

    private static void downloadReport(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod())
                    || !"token=report%2B42".equals(exchange.getRequestURI().getRawQuery())) {
                byte[] error = "Invalid report download link".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
                exchange.sendResponseHeaders(400, error.length);
                exchange.getResponseBody().write(error);
                return;
            }
            byte[] bytes = REPORT.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "text/csv");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=monthly.csv");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
