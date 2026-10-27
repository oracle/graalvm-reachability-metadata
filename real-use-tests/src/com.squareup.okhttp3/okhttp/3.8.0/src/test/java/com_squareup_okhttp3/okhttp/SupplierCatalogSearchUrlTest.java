/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * HTTP URL composition preserves supplier identifiers, search filters, and opaque pagination cursors.
 * A purchasing application searches a supplier's catalog and fetches the next page without
 * interpreting reserved characters in user input or in the service's continuation token.
 */
public class SupplierCatalogSearchUrlTest {

    @Test
    @Timeout(60)
    public void searchesCatalogAndFollowsOpaqueCursorWithoutChangingFilters() throws Exception {
        String supplier = "tea/north";
        String search = "green tea + mint & honey #1";
        String cursor = "batch+2/green tea==&more";
        try (MockWebServer catalog = new MockWebServer()) {
            catalog.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/plain; charset=utf-8")
                    .setHeader("X-Next-Cursor", cursor)
                    .setBody("TEA-42\nTEA-43\n"));
            catalog.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/plain; charset=utf-8")
                    .setBody("TEA-44\n"));
            catalog.start();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
            try {
                HttpUrl searchUrl = catalog.url("/api/suppliers/").newBuilder()
                        .addPathSegment(supplier)
                        .addPathSegment("catalog")
                        .addQueryParameter("q", search)
                        .addQueryParameter("category", "loose leaf")
                        .addQueryParameter("category", "gift/sets")
                        .build();
                List<String> products = new ArrayList<>();
                String continuation;
                Request firstPage = new Request.Builder().url(searchUrl).build();
                try (Response response = client.newCall(firstPage).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    products.addAll(response.body().string().lines().toList());
                    continuation = response.header("X-Next-Cursor");
                    assertThat(continuation).isEqualTo(cursor);
                }

                HttpUrl nextPageUrl = searchUrl.newBuilder()
                        .addQueryParameter("cursor", continuation)
                        .build();
                Request nextPage = firstPage.newBuilder().url(nextPageUrl).build();
                try (Response response = client.newCall(nextPage).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    products.addAll(response.body().string().lines().toList());
                    assertThat(response.header("X-Next-Cursor")).isNull();
                }
                assertThat(products).containsExactly("TEA-42", "TEA-43", "TEA-44");

                RecordedRequest initial = catalog.takeRequest(10, TimeUnit.SECONDS);
                RecordedRequest continued = catalog.takeRequest(10, TimeUnit.SECONDS);
                assertThat(initial).isNotNull();
                assertThat(continued).isNotNull();
                String initialTarget = "/api/suppliers/tea%2Fnorth/catalog"
                        + "?q=green%20tea%20%2B%20mint%20%26%20honey%20%231"
                        + "&category=loose%20leaf&category=gift/sets";
                assertThat(initial.getPath()).isEqualTo(initialTarget);
                assertThat(continued.getPath()).isEqualTo(initialTarget
                        + "&cursor=batch%2B2/green%20tea%3D%3D%26more");
                for (RecordedRequest received : new RecordedRequest[] {initial, continued}) {
                    assertThat(received.getMethod()).isEqualTo("GET");
                    HttpUrl receivedUrl = received.getRequestUrl();
                    assertThat(receivedUrl.pathSegments())
                            .containsExactly("api", "suppliers", supplier, "catalog");
                    assertThat(receivedUrl.queryParameter("q")).isEqualTo(search);
                    assertThat(receivedUrl.queryParameterValues("category"))
                            .containsExactly("loose leaf", "gift/sets");
                    assertThat(receivedUrl.fragment()).isNull();
                }
                assertThat(initial.getRequestUrl().queryParameter("cursor")).isNull();
                assertThat(continued.getRequestUrl().queryParameter("cursor")).isEqualTo(cursor);
                assertThat(searchUrl.queryParameter("cursor")).isNull();
                assertThat(catalog.getRequestCount()).isEqualTo(2);
            } finally {
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }
}
