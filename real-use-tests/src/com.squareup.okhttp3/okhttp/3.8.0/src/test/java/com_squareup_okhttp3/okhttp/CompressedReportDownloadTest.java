/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Transparent gzip response decoding downloads a compressed sales report as a usable CSV file.
 * A reporting application negotiates compression and streams a chunked download to disk
 * without handling compression or HTTP transfer framing itself.
 */
public class CompressedReportDownloadTest {

    @Test
    @Timeout(60)
    public void savesDecodedReportFromChunkedGzipResponse(@TempDir Path directory) throws Exception {
        String report = """
                order,total,currency
                ORD-42,19.95,EUR
                ORD-43,42.50,USD
                """;
        byte[] reportBytes = report.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(reportBytes);
        }

        try (MockWebServer reportingService = new MockWebServer()) {
            reportingService.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/csv; charset=utf-8")
                    .setHeader("Content-Encoding", "gzip")
                    .setHeader("Content-Disposition", "attachment; filename=orders.csv")
                    .setChunkedBody(new Buffer().write(compressed.toByteArray()), 13));
            reportingService.start();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
            try {
                Request download = new Request.Builder()
                        .url(reportingService.url("/reports/orders.csv"))
                        .header("Accept", "text/csv")
                        .build();
                Path destination = directory.resolve("orders.csv");
                try (Response response = client.newCall(download).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.header("Content-Type")).isEqualTo("text/csv; charset=utf-8");
                    assertThat(response.header("Content-Disposition"))
                            .isEqualTo("attachment; filename=orders.csv");
                    // The application sees decoded bytes, not the compressed representation's headers.
                    assertThat(response.header("Content-Encoding")).isNull();
                    assertThat(response.header("Content-Length")).isNull();
                    try (InputStream content = response.body().byteStream()) {
                        assertThat(Files.copy(content, destination)).isEqualTo(reportBytes.length);
                    }
                }
                assertThat(Files.readAllBytes(destination)).isEqualTo(reportBytes);
                assertThat(Files.readString(destination, StandardCharsets.UTF_8)).isEqualTo(report);

                RecordedRequest received = reportingService.takeRequest(10, TimeUnit.SECONDS);
                assertThat(received).isNotNull();
                assertThat(received.getMethod()).isEqualTo("GET");
                assertThat(received.getPath()).isEqualTo("/reports/orders.csv");
                assertThat(received.getHeader("Accept")).isEqualTo("text/csv");
                assertThat(received.getHeader("Accept-Encoding")).isEqualTo("gzip");
                assertThat(reportingService.getRequestCount()).isEqualTo(1);
            } finally {
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }
}
