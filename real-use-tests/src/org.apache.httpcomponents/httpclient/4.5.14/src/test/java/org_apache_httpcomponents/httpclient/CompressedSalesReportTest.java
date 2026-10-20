/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises HttpClient's response decompression subsystem by downloading a gzip-compressed sales report.
public class CompressedSalesReportTest {
    @Test
    @Timeout(60)
    public void negotiatesCompressionAndReturnsTheDecodedReportToTheApplication() throws IOException {
        String report = """
                region,product,units,total
                North,Caf\u00e9 blend,12,144.00
                South,Tea sampler,8,64.00
                """;
        byte[] compressedReport;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
                gzip.write(report.getBytes(StandardCharsets.UTF_8));
            }
            compressedReport = buffer.toByteArray();
        }

        List<String> negotiatedEncodings = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/reports/daily-sales.csv", exchange -> {
            String accepted = exchange.getRequestHeaders().getFirst("Accept-Encoding");
            if (accepted == null || !accepted.toLowerCase(Locale.ROOT).contains("gzip")) {
                respond(exchange, 406, "This report requires gzip support".getBytes(StandardCharsets.UTF_8));
                return;
            }
            negotiatedEncodings.add("gzip");
            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=daily-sales.csv");
            respond(exchange, 200, compressedReport);
        });
        server.start();

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(10_000)
                .setConnectionRequestTimeout(10_000)
                .build();
        String reportUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/reports/daily-sales.csv";
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(config).build()) {
            try (CloseableHttpResponse response = client.execute(new HttpGet(reportUrl))) {
                assertEquals(200, response.getStatusLine().getStatusCode());
                assertEquals("attachment; filename=daily-sales.csv",
                        response.getFirstHeader("Content-Disposition").getValue());
                assertEquals("text/csv; charset=UTF-8", response.getEntity().getContentType().getValue());
                String downloadedReport = EntityUtils.toString(response.getEntity());
                assertEquals(report, downloadedReport);
                assertEquals(List.of("region,product,units,total", "North,Caf\u00e9 blend,12,144.00",
                        "South,Tea sampler,8,64.00"), downloadedReport.lines().toList());
            }
            assertEquals(List.of("gzip"), negotiatedEncodings);
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
