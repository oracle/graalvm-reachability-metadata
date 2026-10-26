/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Connection pooling reuses a supplier connection and reconnects when the supplier retires it.
 * A purchasing application checks stock, places an order on the same connection, and retrieves
 * its confirmation after the supplier requests that the order connection be closed.
 */
public class PurchaseConnectionReuseTest {

    @Test
    @Timeout(60)
    public void reusesStockConnectionForPurchaseAndReconnectsForConfirmation() throws Exception {
        try (MockWebServer supplier = new MockWebServer()) {
            supplier.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"sku\":\"tea-green\",\"available\":18}"));
            supplier.enqueue(new MockResponse()
                    .setResponseCode(201)
                    .setHeader("Location", "/purchases/PO-42")
                    .setHeader("Connection", "close")
                    .setBody("Purchase PO-42 accepted"));
            supplier.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/plain; charset=utf-8")
                    .setBody("Purchase PO-42: 2 units of tea-green reserved"));
            supplier.start();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectionPool(new ConnectionPool(2, 5, TimeUnit.MINUTES))
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
            try {
                Request stock = new Request.Builder()
                        .url(supplier.url("/stock/tea-green"))
                        .build();
                try (Response response = client.newCall(stock).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string())
                            .isEqualTo("{\"sku\":\"tea-green\",\"available\":18}");
                }

                Request purchase = new Request.Builder()
                        .url(supplier.url("/purchases"))
                        .post(RequestBody.create(MediaType.parse("application/json"),
                                "{\"sku\":\"tea-green\",\"quantity\":2}"))
                        .build();
                String confirmationPath;
                try (Response response = client.newCall(purchase).execute()) {
                    assertThat(response.code()).isEqualTo(201);
                    assertThat(response.body().string()).isEqualTo("Purchase PO-42 accepted");
                    confirmationPath = response.header("Location");
                    assertThat(confirmationPath).isEqualTo("/purchases/PO-42");
                }

                Request confirmation = new Request.Builder()
                        .url(supplier.url(confirmationPath))
                        .build();
                try (Response response = client.newCall(confirmation).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string())
                            .isEqualTo("Purchase PO-42: 2 units of tea-green reserved");
                }

                RecordedRequest checked = supplier.takeRequest(10, TimeUnit.SECONDS);
                RecordedRequest ordered = supplier.takeRequest(10, TimeUnit.SECONDS);
                RecordedRequest confirmed = supplier.takeRequest(10, TimeUnit.SECONDS);
                assertThat(checked).isNotNull();
                assertThat(checked.getMethod()).isEqualTo("GET");
                assertThat(checked.getPath()).isEqualTo("/stock/tea-green");
                assertThat(ordered).isNotNull();
                assertThat(ordered.getMethod()).isEqualTo("POST");
                assertThat(ordered.getPath()).isEqualTo("/purchases");
                assertThat(ordered.getBody().readUtf8())
                        .isEqualTo("{\"sku\":\"tea-green\",\"quantity\":2}");
                assertThat(confirmed).isNotNull();
                assertThat(confirmed.getMethod()).isEqualTo("GET");
                assertThat(confirmed.getPath()).isEqualTo("/purchases/PO-42");

                // The server numbers requests from zero separately on each accepted connection.
                assertThat(checked.getSequenceNumber()).isZero();
                assertThat(ordered.getSequenceNumber()).isEqualTo(1);
                assertThat(confirmed.getSequenceNumber()).isZero();
                assertThat(supplier.getRequestCount()).isEqualTo(3);
            } finally {
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }
}
