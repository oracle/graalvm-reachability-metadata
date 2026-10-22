/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Dns;
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
 * Custom DNS resolution routes private service names to registered endpoints without rewriting URLs.
 * An internal shipping client retains the virtual host expected by its service and refuses
 * unregistered names instead of falling back to public DNS.
 */
public class PrivateServiceDiscoveryTest {

    @Test
    @Timeout(60)
    public void fetchesShippingQuoteThroughPrivateServiceDirectory() throws Exception {
        InetAddress endpoint = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        String serviceName = "shipping.services.test";
        try (MockWebServer shippingService = new MockWebServer()) {
            shippingService.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"currency\":\"EUR\",\"shipping\":4.95}"));
            shippingService.start(endpoint, 0);

            OkHttpClient client = new OkHttpClient.Builder()
                    .dns(new ServiceDirectoryDns(Map.of(serviceName, List.of(endpoint))))
                    .proxy(Proxy.NO_PROXY)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
            try {
                HttpUrl quoteUrl = shippingService.url("/quotes/order-42").newBuilder()
                        .host(serviceName)
                        .addQueryParameter("country", "DE")
                        .build();
                Request quote = new Request.Builder().url(quoteUrl).build();
                try (Response response = client.newCall(quote).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string())
                            .isEqualTo("{\"currency\":\"EUR\",\"shipping\":4.95}");
                    assertThat(response.request().url().host()).isEqualTo(serviceName);
                }

                RecordedRequest received = shippingService.takeRequest(10, TimeUnit.SECONDS);
                assertThat(received).isNotNull();
                assertThat(received.getMethod()).isEqualTo("GET");
                assertThat(received.getPath()).isEqualTo("/quotes/order-42?country=DE");
                assertThat(received.getHeader("Host"))
                        .isEqualTo(serviceName + ":" + shippingService.getPort());

                Request unregisteredService = quote.newBuilder()
                        .url(quoteUrl.newBuilder().host("retired.services.test").build())
                        .build();
                assertThatExceptionOfType(UnknownHostException.class).isThrownBy(() -> {
                    try (Response response = client.newCall(unregisteredService).execute()) {
                        response.body().string();
                    }
                }).withMessage("No registered endpoint for retired.services.test");
                assertThat(shippingService.getRequestCount()).isEqualTo(1);
            } finally {
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }

    private static final class ServiceDirectoryDns implements Dns {
        private final Map<String, List<InetAddress>> endpoints;

        private ServiceDirectoryDns(Map<String, List<InetAddress>> endpoints) {
            this.endpoints = Map.copyOf(endpoints);
        }

        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            List<InetAddress> addresses = endpoints.get(hostname);
            if (addresses == null) {
                throw new UnknownHostException("No registered endpoint for " + hostname);
            }
            return addresses;
        }
    }
}
