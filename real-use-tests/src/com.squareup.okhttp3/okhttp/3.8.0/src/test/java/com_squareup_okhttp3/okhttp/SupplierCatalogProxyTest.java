/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;
import okhttp3.Credentials;
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
 * HTTP proxy routing retrieves a supplier catalog through an authenticated corporate gateway.
 * The application leaves the supplier hostname for the gateway and answers its proxy
 * challenge without sending origin credentials.
 */
public class SupplierCatalogProxyTest {

    @Test
    @Timeout(60)
    public void retrievesSupplierCatalogThroughChallengingProxy() throws Exception {
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        HttpUrl catalogUrl = HttpUrl.parse("http://supplier.intranet.test/catalog?warehouse=north");
        String catalog = """
                {"supplier":"tea-co","items":[{"sku":"tea-green","available":180}]}
                """;
        try (MockWebServer gateway = new MockWebServer()) {
            gateway.enqueue(new MockResponse()
                    .setResponseCode(407)
                    .setHeader("Proxy-Authenticate", "Basic realm=\"corporate-gateway\"")
                    .setBody("Proxy credentials required"));
            gateway.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(catalog));
            gateway.start(loopback, 0);

            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(loopback, gateway.getPort()));
            OkHttpClient client = new OkHttpClient.Builder()
                    .proxy(proxy)
                    .proxyAuthenticator((route, response) -> {
                        if (response.request().header("Proxy-Authorization") != null) {
                            return null;
                        }
                        return response.request().newBuilder()
                                .header("Proxy-Authorization", Credentials.basic("catalog-reader", "proxy-secret"))
                                .build();
                    })
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
            try {
                Request catalogRequest = new Request.Builder()
                        .url(catalogUrl)
                        .header("Accept", "application/json")
                        .build();
                try (Response response = client.newCall(catalogRequest).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string()).isEqualTo(catalog);
                    assertThat(response.request().url()).isEqualTo(catalogUrl);
                    assertThat(response.priorResponse()).isNotNull();
                    assertThat(response.priorResponse().code()).isEqualTo(407);
                    assertThat(response.priorResponse().priorResponse()).isNull();
                }

                RecordedRequest anonymous = gateway.takeRequest(10, TimeUnit.SECONDS);
                RecordedRequest authenticated = gateway.takeRequest(10, TimeUnit.SECONDS);
                assertThat(anonymous).isNotNull();
                assertThat(authenticated).isNotNull();
                for (RecordedRequest request : new RecordedRequest[] {anonymous, authenticated}) {
                    assertThat(request.getRequestLine()).isEqualTo("GET " + catalogUrl + " HTTP/1.1");
                    assertThat(request.getHeader("Host")).isEqualTo("supplier.intranet.test");
                    assertThat(request.getHeader("Accept")).isEqualTo("application/json");
                    assertThat(request.getHeader("Authorization")).isNull();
                    assertThat(request.getBodySize()).isZero();
                }
                assertThat(anonymous.getHeader("Proxy-Authorization")).isNull();
                assertThat(authenticated.getHeader("Proxy-Authorization"))
                        .isEqualTo(Credentials.basic("catalog-reader", "proxy-secret"));
                assertThat(gateway.getRequestCount()).isEqualTo(2);
            } finally {
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }
}
