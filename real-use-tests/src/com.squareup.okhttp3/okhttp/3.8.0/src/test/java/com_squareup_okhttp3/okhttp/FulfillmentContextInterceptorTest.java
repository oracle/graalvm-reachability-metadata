/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
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
 * Application interceptors propagate tenant context and translate fulfillment service errors.
 * A shared fulfillment client attaches each order's tenant and correlation identifier without
 * putting service credentials in the calling application's request construction.
 */
public class FulfillmentContextInterceptorTest {

    @Test
    @Timeout(60)
    public void submitsTenantOrdersAndSurfacesRejectedShipmentDetails() throws Exception {
        try (MockWebServer fulfillment = new MockWebServer()) {
            fulfillment.enqueue(new MockResponse()
                    .setResponseCode(201)
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Location", "/shipments/SHIP-42")
                    .setBody("{\"shipment\":\"SHIP-42\",\"status\":\"booked\"}"));
            fulfillment.enqueue(new MockResponse()
                    .setResponseCode(409)
                    .setHeader("Content-Type", "text/plain; charset=utf-8")
                    .setBody("Order ORD-43 already has a shipment"));
            fulfillment.start();

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new FulfillmentInterceptor("fulfillment-service-token"))
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
            try {
                Request firstOrder = new Request.Builder()
                        .url(fulfillment.url("/shipments"))
                        .tag(new TenantContext("north-store", "checkout-42"))
                        .post(RequestBody.create(MediaType.parse("application/json"), "{\"order\":\"ORD-42\"}"))
                        .build();
                try (Response response = client.newCall(firstOrder).execute()) {
                    assertThat(response.code()).isEqualTo(201);
                    assertThat(response.header("Location")).isEqualTo("/shipments/SHIP-42");
                    assertThat(response.body().string())
                            .isEqualTo("{\"shipment\":\"SHIP-42\",\"status\":\"booked\"}");
                }

                Request secondOrder = firstOrder.newBuilder()
                        .tag(new TenantContext("south-store", "checkout-43"))
                        .post(RequestBody.create(MediaType.parse("application/json"), "{\"order\":\"ORD-43\"}"))
                        .build();
                assertThatThrownBy(() -> {
                    try (Response response = client.newCall(secondOrder).execute()) {
                        throw new AssertionError("Expected the service rejection, got HTTP " + response.code());
                    }
                }).isInstanceOf(IOException.class)
                        .hasMessage("Fulfillment checkout-43 returned HTTP 409: Order ORD-43 already has a shipment");

                RecordedRequest first = fulfillment.takeRequest(10, TimeUnit.SECONDS);
                RecordedRequest second = fulfillment.takeRequest(10, TimeUnit.SECONDS);
                assertThat(first).isNotNull();
                assertThat(second).isNotNull();
                for (RecordedRequest received : new RecordedRequest[] {first, second}) {
                    assertThat(received.getMethod()).isEqualTo("POST");
                    assertThat(received.getPath()).isEqualTo("/shipments");
                    assertThat(received.getHeader("Authorization")).isEqualTo("Bearer fulfillment-service-token");
                    assertThat(received.getHeader("Content-Type")).isEqualTo("application/json; charset=utf-8");
                }
                assertThat(first.getHeader("X-Tenant-Id")).isEqualTo("north-store");
                assertThat(first.getHeader("X-Correlation-Id")).isEqualTo("checkout-42");
                assertThat(first.getBody().readUtf8()).isEqualTo("{\"order\":\"ORD-42\"}");
                assertThat(second.getHeader("X-Tenant-Id")).isEqualTo("south-store");
                assertThat(second.getHeader("X-Correlation-Id")).isEqualTo("checkout-43");
                assertThat(second.getBody().readUtf8()).isEqualTo("{\"order\":\"ORD-43\"}");
                assertThat(firstOrder.header("Authorization")).isNull();
                assertThat(firstOrder.header("X-Tenant-Id")).isNull();
                assertThat(fulfillment.getRequestCount()).isEqualTo(2);
            } finally {
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }

    private record TenantContext(String tenant, String correlationId) {
    }

    private static final class FulfillmentInterceptor implements Interceptor {
        private final String token;

        private FulfillmentInterceptor(String token) {
            this.token = token;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request original = chain.request();
            TenantContext context = (TenantContext) original.tag();
            Request authenticated = original.newBuilder()
                    .header("Authorization", "Bearer " + token)
                    .header("X-Tenant-Id", context.tenant())
                    .header("X-Correlation-Id", context.correlationId())
                    .build();
            Response response = chain.proceed(authenticated);
            if (response.isSuccessful()) {
                return response;
            }
            try (response) {
                throw new IOException("Fulfillment " + context.correlationId() + " returned HTTP "
                        + response.code() + ": " + response.body().string());
            }
        }
    }
}
