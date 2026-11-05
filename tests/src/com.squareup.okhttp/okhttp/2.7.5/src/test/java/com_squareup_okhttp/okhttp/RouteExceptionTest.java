/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp.okhttp;

import java.net.InetAddress;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import com.squareup.okhttp.CipherSuite;
import com.squareup.okhttp.ConnectionPool;
import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.TlsVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RouteExceptionTest {
    @Test
    @Timeout(60)
    public void retainsEarlierHandshakeFailureWhenTlsFallbackIsExhausted() throws Exception {
        SSLContext context = TlsTestSupport.context();
        ConnectionPool pool = new ConnectionPool(0, 10_000);
        try (SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory()
                .createServerSocket(0, 2, InetAddress.getByName("127.0.0.1"));
                ExecutorService executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(10_000);
            server.setEnabledProtocols(new String[]{"TLSv1.3"});
            Future<Integer> attempts = executor.submit(() -> {
                for (int attempt = 0; attempt < 2; attempt++) {
                    try (SSLSocket socket = (SSLSocket) server.accept()) {
                        socket.setSoTimeout(10_000);
                        assertThrows(SSLHandshakeException.class, socket::startHandshake);
                    }
                }
                return 2;
            });
            ConnectionSpec first = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .cipherSuites(CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256)
                    .build();
            ConnectionSpec fallback = new ConnectionSpec.Builder(first)
                    .allEnabledCipherSuites()
                    .build();
            OkHttpClient client = new OkHttpClient()
                    .setSslSocketFactory(context.getSocketFactory())
                    .setProxy(Proxy.NO_PROXY)
                    .setConnectionPool(pool)
                    .setConnectionSpecs(List.of(first, fallback))
                    .setProtocols(List.of(Protocol.HTTP_1_1));
            client.setRetryOnConnectionFailure(true);
            client.setConnectTimeout(10, TimeUnit.SECONDS);
            client.setReadTimeout(10, TimeUnit.SECONDS);
            client.setWriteTimeout(10, TimeUnit.SECONDS);
            Request request = new Request.Builder()
                    .url("https://127.0.0.1:" + server.getLocalPort() + "/")
                    .build();

            SSLHandshakeException failure = assertThrows(SSLHandshakeException.class,
                    () -> client.newCall(request).execute());

            assertEquals(1, failure.getSuppressed().length);
            assertInstanceOf(SSLHandshakeException.class, failure.getSuppressed()[0]);
            assertNotSame(failure, failure.getSuppressed()[0]);
            assertEquals(2, attempts.get(20, TimeUnit.SECONDS));
        } finally {
            pool.evictAll();
        }
    }
}
