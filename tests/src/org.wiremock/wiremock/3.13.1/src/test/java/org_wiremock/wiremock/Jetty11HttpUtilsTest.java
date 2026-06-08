/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wiremock.wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.http.Fault.EMPTY_RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;

public class Jetty11HttpUtilsTest {
    @Test
    void httpsFaultResponseClosesTheUnderlyingTlsSocket() throws Exception {
        AtomicInteger requestsReceived = new AtomicInteger();
        WireMockServer server = new WireMockServer(wireMockConfig()
                .dynamicPort()
                .dynamicHttpsPort());
        server.addMockServiceRequestListener(
                (request, response) -> requestsReceived.incrementAndGet());

        try {
            server.start();
            server.stubFor(get(urlEqualTo("/empty-response"))
                    .willReturn(aResponse().withFault(EMPTY_RESPONSE)));

            Throwable thrown = catchThrowable(
                    () -> getOverTrustedHttps(server.httpsPort(), "/empty-response"));

            assertThat(thrown).isInstanceOf(IOException.class);
            assertThat(requestsReceived.get()).isGreaterThanOrEqualTo(1);
        } finally {
            server.stop();
        }
    }

    private static int getOverTrustedHttps(int port, String path) throws Exception {
        URI uri = URI.create("https://localhost:" + port + path);
        HttpsURLConnection connection = (HttpsURLConnection) uri.toURL().openConnection(Proxy.NO_PROXY);
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        connection.setSSLSocketFactory(trustAllSslContext().getSocketFactory());
        connection.setHostnameVerifier(trustAllHostnames());

        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    private static SSLContext trustAllSslContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] {new TrustAllCertificates()}, new SecureRandom());
        return sslContext;
    }

    private static HostnameVerifier trustAllHostnames() {
        return (hostname, session) -> true;
    }

    private static final class TrustAllCertificates implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            assertThat(chain).isNotNull();
            assertThat(authType).isNotBlank();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            assertThat(chain).isNotEmpty();
            assertThat(authType).isNotBlank();
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
