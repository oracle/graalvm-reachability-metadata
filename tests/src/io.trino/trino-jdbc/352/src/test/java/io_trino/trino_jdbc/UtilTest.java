/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.okhttp3.Call;
import io.trino.jdbc.$internal.okhttp3.ConnectionSpec;
import io.trino.jdbc.$internal.okhttp3.EventListener;
import io.trino.jdbc.$internal.okhttp3.OkHttpClient;
import io.trino.jdbc.$internal.okhttp3.Protocol;
import io.trino.jdbc.$internal.okhttp3.Request;
import io.trino.jdbc.$internal.okhttp3.Response;
import io.trino.jdbc.$internal.okhttp3.TlsVersion;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UtilTest {
    @Test
    @Timeout(120)
    void retainsHandshakeFailuresWhenTlsFallbackSucceeds() throws Exception {
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream input = UtilTest.class.getResourceAsStream("localhost.p12")) {
            keys.load(input, "changeit".toCharArray());
        }
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(keys);
        X509TrustManager trustManager = (X509TrustManager) managers.getTrustManagers()[0];
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, managers.getTrustManagers(), null);
        ConnectionSpec tls13 = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_3).build();
        ConnectionSpec tls12 = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2).build();
        List<IOException> failures = new ArrayList<>();
        OkHttpClient client = new OkHttpClient.Builder()
                .sslSocketFactory(context.getSocketFactory(), trustManager)
                .connectionSpecs(List.of(tls13, tls13, tls12))
                .protocols(List.of(Protocol.HTTP_1_1))
                .proxy(Proxy.NO_PROXY)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .eventListener(new EventListener() {
                    @Override
                    public void connectFailed(Call call, InetSocketAddress address, Proxy proxy,
                            Protocol protocol, IOException failure) {
                        failures.add(failure);
                    }
                })
                .build();
        try (TrinoProtocolServer server = new TrinoProtocolServer(true, "TLSv1.2")) {
            String url = server.jdbcUrl().replace("jdbc:trino:", "https:");
            url = url.substring(0, url.indexOf("/memory/default")) + "/v1/statement";
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                assertEquals(200, response.code());
                assertTrue(response.body().string().contains("metadata_query"));
            }
            assertEquals(2, failures.size());
            assertInstanceOf(SSLException.class, failures.get(0));
            assertInstanceOf(SSLException.class, failures.get(1));
            assertEquals(1, failures.get(0).getSuppressed().length);
            assertSame(failures.get(1), failures.get(0).getSuppressed()[0]);
            assertEquals(1, server.statements.size());
        } finally {
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdownNow();
        }
    }
}
