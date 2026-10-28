/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * HTTP disk caching revalidates a catalog with an ETag and serves it without the network.
 * A catalog reader stores a response, explicitly refreshes it, and uses the retained body
 * when the origin reports that the representation has not changed.
 */
public class CatalogCacheRevalidationTest {

    @Test
    @Timeout(60)
    public void refreshesCatalogAndReadsItInCacheOnlyMode(@TempDir Path directory) throws Exception {
        String catalog = """
                {"items":[{"sku":"tea-green","price":425}]}
                """;
        try (MockWebServer origin = new MockWebServer();
                Cache cache = new Cache(directory.resolve("catalog-cache").toFile(), 1024 * 1024)) {
            origin.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Cache-Control", "max-age=0")
                    .setHeader("ETag", "\"catalog-revision-7\"")
                    .setBody(catalog));
            origin.enqueue(new MockResponse()
                    .setResponseCode(304)
                    .setHeader("Cache-Control", "max-age=3600")
                    .setHeader("ETag", "\"catalog-revision-7\"")
                    .setHeader("X-Catalog-Status", "validated"));
            origin.start();

            OkHttpClient client = new OkHttpClient.Builder()
                    .cache(cache)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
            try {
                Request catalogRequest = new Request.Builder().url(origin.url("/catalog")).build();
                try (Response response = client.newCall(catalogRequest).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string()).isEqualTo(catalog);
                    assertThat(response.cacheResponse()).isNull();
                }

                // A zero freshness allowance forces validation without waiting for a clock tick.
                Request refresh = catalogRequest.newBuilder()
                        .cacheControl(new CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build())
                        .build();
                try (Response response = client.newCall(refresh).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string()).isEqualTo(catalog);
                    assertThat(response.networkResponse()).isNotNull();
                    assertThat(response.networkResponse().code()).isEqualTo(304);
                    assertThat(response.cacheResponse()).isNotNull();
                    assertThat(response.header("X-Catalog-Status")).isEqualTo("validated");
                }

                Request offline = catalogRequest.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build();
                try (Response response = client.newCall(offline).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string()).isEqualTo(catalog);
                    assertThat(response.networkResponse()).isNull();
                    assertThat(response.cacheResponse()).isNotNull();
                    assertThat(response.header("X-Catalog-Status")).isEqualTo("validated");
                }

                RecordedRequest initial = origin.takeRequest(10, TimeUnit.SECONDS);
                RecordedRequest validation = origin.takeRequest(10, TimeUnit.SECONDS);
                assertThat(initial).isNotNull();
                assertThat(initial.getPath()).isEqualTo("/catalog");
                assertThat(initial.getHeader("If-None-Match")).isNull();
                assertThat(validation).isNotNull();
                assertThat(validation.getPath()).isEqualTo("/catalog");
                assertThat(validation.getHeader("If-None-Match")).isEqualTo("\"catalog-revision-7\"");
                assertThat(origin.getRequestCount()).isEqualTo(2);
            } finally {
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }
}
