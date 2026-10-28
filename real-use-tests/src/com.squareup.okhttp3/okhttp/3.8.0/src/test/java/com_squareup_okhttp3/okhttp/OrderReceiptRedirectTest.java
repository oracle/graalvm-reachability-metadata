/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * HTTP redirects turn an order submission into a receipt lookup without leaking origin credentials.
 * A checkout client follows a relative receipt link and then a link to a separate receipt
 * service, which must receive neither the checkout token nor the submitted form.
 */
public class OrderReceiptRedirectTest {

    @Test
    @Timeout(60)
    public void followsReceiptLinksWithoutResubmittingOrderOrForwardingCredentials() throws Exception {
        try (MockWebServer checkout = new MockWebServer();
                MockWebServer receipts = new MockWebServer()) {
            receipts.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/plain; charset=utf-8")
                    .setBody("Receipt ORD-42: paid, total USD 19.95"));
            receipts.start();
            checkout.enqueue(new MockResponse()
                    .setResponseCode(303)
                    .setHeader("Location", "../receipts/ORD-42")
                    .setBody("Order accepted"));
            checkout.enqueue(new MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", receipts.url("/archive/ORD-42"))
                    .setBody("Receipt archived"));
            checkout.start();

            OkHttpClient client = new OkHttpClient.Builder()
                    .followRedirects(true)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
            try {
                Request order = new Request.Builder()
                        .url(checkout.url("/checkout/orders"))
                        .header("Authorization", "Bearer checkout-token")
                        .post(new FormBody.Builder()
                                .add("sku", "tea-green")
                                .add("quantity", "2")
                                .build())
                        .build();
                try (Response response = client.newCall(order).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string()).isEqualTo("Receipt ORD-42: paid, total USD 19.95");
                    assertThat(response.request().url()).isEqualTo(receipts.url("/archive/ORD-42"));
                    assertThat(response.priorResponse()).isNotNull();
                    assertThat(response.priorResponse().code()).isEqualTo(302);
                    assertThat(response.priorResponse().priorResponse()).isNotNull();
                    assertThat(response.priorResponse().priorResponse().code()).isEqualTo(303);
                    assertThat(response.priorResponse().priorResponse().priorResponse()).isNull();
                }

                RecordedRequest submission = checkout.takeRequest(10, TimeUnit.SECONDS);
                assertThat(submission).isNotNull();
                assertThat(submission.getMethod()).isEqualTo("POST");
                assertThat(submission.getPath()).isEqualTo("/checkout/orders");
                assertThat(submission.getHeader("Authorization")).isEqualTo("Bearer checkout-token");
                assertThat(submission.getBody().readUtf8()).isEqualTo("sku=tea-green&quantity=2");

                RecordedRequest receiptLookup = checkout.takeRequest(10, TimeUnit.SECONDS);
                assertThat(receiptLookup).isNotNull();
                assertThat(receiptLookup.getMethod()).isEqualTo("GET");
                assertThat(receiptLookup.getPath()).isEqualTo("/receipts/ORD-42");
                assertThat(receiptLookup.getHeader("Authorization")).isEqualTo("Bearer checkout-token");
                assertThat(receiptLookup.getHeader("Content-Type")).isNull();
                assertThat(receiptLookup.getBodySize()).isZero();

                RecordedRequest archivedReceipt = receipts.takeRequest(10, TimeUnit.SECONDS);
                assertThat(archivedReceipt).isNotNull();
                assertThat(archivedReceipt.getMethod()).isEqualTo("GET");
                assertThat(archivedReceipt.getPath()).isEqualTo("/archive/ORD-42");
                assertThat(archivedReceipt.getHeader("Authorization")).isNull();
                assertThat(archivedReceipt.getHeader("Content-Type")).isNull();
                assertThat(archivedReceipt.getBodySize()).isZero();
                assertThat(checkout.getRequestCount()).isEqualTo(2);
                assertThat(receipts.getRequestCount()).isEqualTo(1);
            } finally {
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }
}
