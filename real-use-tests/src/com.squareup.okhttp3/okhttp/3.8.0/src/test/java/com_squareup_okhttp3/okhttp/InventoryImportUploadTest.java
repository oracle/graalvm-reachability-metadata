/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Multipart request bodies upload an inventory CSV file alongside import options.
 * An inventory application streams a local export to an import endpoint and reads
 * the job identifier returned by the service.
 */
public class InventoryImportUploadTest {

    @Test
    @Timeout(60)
    public void uploadsInventoryFileWithWarehouseAndImportMode(@TempDir Path directory) throws Exception {
        String inventory = """
                sku,quantity
                tea-green,18
                coffee-dark,24
                """;
        Path export = directory.resolve("inventory.csv");
        Files.writeString(export, inventory, StandardCharsets.UTF_8);

        try (MockWebServer importer = new MockWebServer()) {
            importer.enqueue(new MockResponse()
                    .setResponseCode(202)
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Location", "/imports/job-42")
                    .setBody("{\"jobId\":\"job-42\",\"status\":\"queued\"}"));
            importer.start();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
            try {
                MultipartBody upload = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("warehouse", "north-depot")
                        .addFormDataPart("mode", "replace")
                        .addFormDataPart("file", export.getFileName().toString(),
                                RequestBody.create(MediaType.parse("text/csv; charset=utf-8"), export.toFile()))
                        .build();
                Request request = new Request.Builder()
                        .url(importer.url("/imports"))
                        .post(upload)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    assertThat(response.code()).isEqualTo(202);
                    assertThat(response.header("Location")).isEqualTo("/imports/job-42");
                    assertThat(response.body().string())
                            .isEqualTo("{\"jobId\":\"job-42\",\"status\":\"queued\"}");
                }

                RecordedRequest received = importer.takeRequest(10, TimeUnit.SECONDS);
                assertThat(received).isNotNull();
                assertThat(received.getMethod()).isEqualTo("POST");
                assertThat(received.getPath()).isEqualTo("/imports");
                assertThat(received.getHeader("Content-Type"))
                        .isEqualTo("multipart/form-data; boundary=" + upload.boundary());
                assertThat(received.getHeader("Content-Length"))
                        .isEqualTo(Long.toString(received.getBodySize()));
                assertThat(received.getBody().readUtf8())
                        .startsWith("--" + upload.boundary() + "\r\n")
                        .contains("Content-Disposition: form-data; name=\"warehouse\"\r\n"
                                + "Content-Length: 11\r\n\r\nnorth-depot\r\n")
                        .contains("Content-Disposition: form-data; name=\"mode\"\r\n"
                                + "Content-Length: 7\r\n\r\nreplace\r\n")
                        .contains("Content-Disposition: form-data; name=\"file\"; filename=\"inventory.csv\"\r\n"
                                + "Content-Type: text/csv; charset=utf-8\r\n"
                                + "Content-Length: " + Files.size(export) + "\r\n\r\n" + inventory + "\r\n")
                        .endsWith("--" + upload.boundary() + "--\r\n");
                assertThat(importer.getRequestCount()).isEqualTo(1);
            } finally {
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }
}
