/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Asynchronous HTTP calls fan out stock and price lookups for a product page.
 * A storefront combines independently delivered response bodies without blocking a
 * callback thread on another HTTP request.
 */
public class ProductPageAsyncLookupTest {

    @Test
    @Timeout(60)
    public void combinesConcurrentStockAndPriceResponses() throws Exception {
        try (MockWebServer services = new MockWebServer()) {
            services.setDispatcher(new ProductServices());
            services.start();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .build();
            try {
                Request stockRequest = new Request.Builder()
                        .url(services.url("/stock/tea-green"))
                        .header("X-Store", "north-depot")
                        .build();
                Request priceRequest = new Request.Builder()
                        .url(services.url("/prices/tea-green"))
                        .header("X-Store", "north-depot")
                        .build();

                CompletableFuture<String> stock = fetch(client, stockRequest);
                CompletableFuture<String> price = fetch(client, priceRequest);
                CompletableFuture<String> productPage = stock.thenCombine(price,
                        (quantity, amount) -> "tea-green: " + quantity + " available at " + amount);

                assertThat(productPage.get(20, TimeUnit.SECONDS))
                        .isEqualTo("tea-green: 18 available at USD 4.25");

                RecordedRequest first = services.takeRequest(10, TimeUnit.SECONDS);
                RecordedRequest second = services.takeRequest(10, TimeUnit.SECONDS);
                assertThat(first).isNotNull();
                assertThat(second).isNotNull();
                assertThat(new String[] {first.getPath(), second.getPath()})
                        .containsExactlyInAnyOrder("/stock/tea-green", "/prices/tea-green");
                for (RecordedRequest request : new RecordedRequest[] {first, second}) {
                    assertThat(request.getMethod()).isEqualTo("GET");
                    assertThat(request.getHeader("X-Store")).isEqualTo("north-depot");
                }
                assertThat(services.getRequestCount()).isEqualTo(2);
            } finally {
                client.dispatcher().cancelAll();
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }

    private static CompletableFuture<String> fetch(OkHttpClient client, Request request) {
        BodyCallback callback = new BodyCallback();
        client.newCall(request).enqueue(callback);
        return callback.body;
    }

    private static final class BodyCallback implements Callback {
        private final CompletableFuture<String> body = new CompletableFuture<>();

        @Override
        public void onFailure(Call call, IOException failure) {
            body.completeExceptionally(failure);
        }

        @Override
        public void onResponse(Call call, Response response) {
            String content;
            try (response) {
                if (!response.isSuccessful()) {
                    throw new IOException("Product service returned HTTP " + response.code());
                }
                content = response.body().string();
            } catch (IOException failure) {
                body.completeExceptionally(failure);
                return;
            }
            body.complete(content);
        }
    }

    private static final class ProductServices extends Dispatcher {
        private final CountDownLatch lookups = new CountDownLatch(2);

        @Override
        public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
            // Neither service replies until both lookups have arrived: no timing-based overlap check.
            lookups.countDown();
            if (!lookups.await(10, TimeUnit.SECONDS)) {
                return new MockResponse().setResponseCode(503).setBody("Both lookups are required");
            }
            return switch (request.getPath()) {
                case "/stock/tea-green" -> new MockResponse().setBody("18");
                case "/prices/tea-green" -> new MockResponse().setBody("USD 4.25");
                default -> new MockResponse().setResponseCode(404).setBody("Unknown product service");
            };
        }
    }
}
