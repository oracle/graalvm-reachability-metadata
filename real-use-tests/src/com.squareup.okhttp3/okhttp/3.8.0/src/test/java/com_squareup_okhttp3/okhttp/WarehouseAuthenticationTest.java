/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * HTTP authentication retries a protected warehouse request after a Basic challenge.
 * A warehouse integration sends credentials only after the server requests them and
 * stops retrying if those credentials are rejected.
 */
public class WarehouseAuthenticationTest {

    @Test
    @Timeout(60)
    public void fetchesProtectedStockAndStopsWhenCredentialsAreRejected() throws Exception {
        try (MockWebServer warehouse = new MockWebServer()) {
            warehouse.enqueue(challenge());
            warehouse.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"sku\":\"tea-green\",\"available\":18}"));
            warehouse.enqueue(challenge());
            warehouse.enqueue(challenge());
            warehouse.start();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .authenticator((route, response) -> {
                        if (response.request().header("Authorization") != null) {
                            return null;
                        }
                        return response.request().newBuilder()
                                .header("Authorization", Credentials.basic("stock-reader", "warehouse-secret"))
                                .build();
                    })
                    .build();
            try {
                Request stock = new Request.Builder().url(warehouse.url("/stock/tea-green")).build();
                try (Response response = client.newCall(stock).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string())
                            .isEqualTo("{\"sku\":\"tea-green\",\"available\":18}");
                    assertThat(response.priorResponse()).isNotNull();
                    assertThat(response.priorResponse().code()).isEqualTo(401);
                }

                // The same stored credentials may subsequently be revoked by the service.
                try (Response response = client.newCall(stock).execute()) {
                    assertThat(response.code()).isEqualTo(401);
                    assertThat(response.body().string()).isEqualTo("Authentication required");
                    assertThat(response.priorResponse()).isNotNull();
                    assertThat(response.priorResponse().code()).isEqualTo(401);
                }

                for (int attempt = 0; attempt < 2; attempt++) {
                    RecordedRequest anonymous = warehouse.takeRequest(10, TimeUnit.SECONDS);
                    RecordedRequest authenticated = warehouse.takeRequest(10, TimeUnit.SECONDS);
                    assertThat(anonymous).isNotNull();
                    assertThat(anonymous.getMethod()).isEqualTo("GET");
                    assertThat(anonymous.getPath()).isEqualTo("/stock/tea-green");
                    assertThat(anonymous.getHeader("Authorization")).isNull();
                    assertThat(authenticated).isNotNull();
                    assertThat(authenticated.getMethod()).isEqualTo("GET");
                    assertThat(authenticated.getPath()).isEqualTo("/stock/tea-green");
                    assertThat(authenticated.getHeader("Authorization"))
                            .isEqualTo("Basic c3RvY2stcmVhZGVyOndhcmVob3VzZS1zZWNyZXQ=");
                }
                assertThat(warehouse.getRequestCount()).isEqualTo(4);
            } finally {
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }

    private static MockResponse challenge() {
        return new MockResponse()
                .setResponseCode(401)
                .setHeader("WWW-Authenticate", "Basic realm=\"warehouse\"")
                .setBody("Authentication required");
    }
}
