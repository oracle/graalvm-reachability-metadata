/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import com.sun.net.httpserver.HttpServer;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HttpClient's request cancellation subsystem to abandon a streaming report and download a replacement.
public class CancelledReportDownloadTest {
    @Test
    @Timeout(60)
    public void abortsAnInProgressExportAndMakesTheConnectionSlotAvailableForAnotherReport() throws Exception {
        byte[] reportHeader = "sku,available\n".getBytes(StandardCharsets.UTF_8);
        String replacementReport = "sku,available\nNOTEBOOK-42,18\n";
        CountDownLatch downloadStarted = new CountDownLatch(1);
        CountDownLatch releaseExport = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/reports/full-export", exchange -> {
            try {
                exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write(reportHeader);
                exchange.getResponseBody().flush();
                // Keep the export open until the application has observed cancellation.
                releaseExport.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.createContext("/reports/summary", exchange -> {
            try (exchange) {
                byte[] body = replacementReport.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(30_000)
                .setConnectionRequestTimeout(10_000)
                .build();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ExecutorService downloads = Executors.newSingleThreadExecutor();
        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .setMaxConnTotal(1)
                .setMaxConnPerRoute(1)
                .disableAutomaticRetries()
                .build()) {
            HttpGet export = new HttpGet(baseUrl + "/reports/full-export");
            Future<String> downloadedReport = downloads.submit(() -> {
                try (CloseableHttpResponse response = client.execute(export);
                     DataInputStream body = new DataInputStream(response.getEntity().getContent())) {
                    assertEquals(200, response.getStatusLine().getStatusCode());
                    byte[] receivedHeader = new byte[reportHeader.length];
                    body.readFully(receivedHeader);
                    assertArrayEquals(reportHeader, receivedHeader);
                    downloadStarted.countDown();
                    return new String(reportHeader, StandardCharsets.UTF_8)
                            + new String(body.readAllBytes(), StandardCharsets.UTF_8);
                }
            });
            assertTrue(downloadStarted.await(10, TimeUnit.SECONDS), "The export should start streaming");

            // A user chooses the smaller summary instead of waiting for the full export.
            export.abort();
            ExecutionException cancelled = assertThrows(ExecutionException.class,
                    () -> downloadedReport.get(10, TimeUnit.SECONDS));
            assertInstanceOf(IOException.class, cancelled.getCause());
            releaseExport.countDown();

            String summary = client.execute(new HttpGet(baseUrl + "/reports/summary"), new BasicResponseHandler());
            assertEquals(replacementReport, summary);
        } finally {
            releaseExport.countDown();
            server.stop(0);
            downloads.shutdownNow();
            assertTrue(downloads.awaitTermination(10, TimeUnit.SECONDS), "The download worker should terminate");
        }
    }
}
