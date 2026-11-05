/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp.okhttp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.squareup.okhttp.ConnectionPool;
import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.TlsVersion;
import org.eclipse.jetty.alpn.ALPN;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PlatformInnerJettyNegoProviderTest {
    @Test
    @Timeout(90)
    public void exposesAlpnProviderDiagnosticsDuringHttpsHandshake() throws Exception {
        SSLContext context = TlsTestSupport.context();
        CapturingSocketFactory sockets = new CapturingSocketFactory(context.getSocketFactory());
        ConnectionPool pool = new ConnectionPool(0, 10_000);
        try (SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
                ExecutorService executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(30_000);
            server.setEnabledProtocols(new String[]{"TLSv1.2"});
            ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .supportsTlsExtensions(true)
                    .build();
            OkHttpClient client = new OkHttpClient()
                    .setSslSocketFactory(sockets)
                    .setProxy(Proxy.NO_PROXY)
                    .setConnectionPool(pool)
                    .setConnectionSpecs(List.of(spec))
                    .setProtocols(List.of(Protocol.HTTP_2, Protocol.HTTP_1_1));
            client.setConnectTimeout(30, TimeUnit.SECONDS);
            client.setReadTimeout(30, TimeUnit.SECONDS);
            client.setWriteTimeout(30, TimeUnit.SECONDS);
            Request request = new Request.Builder()
                    .url("https://127.0.0.1:" + server.getLocalPort() + "/alpn-diagnostics")
                    .build();
            Future<String> reply = executor.submit(() -> {
                Response response = client.newCall(request).execute();
                try (ResponseBody body = response.body()) {
                    assertEquals(200, response.code());
                    assertEquals(Protocol.HTTP_1_1, response.protocol());
                    assertEquals(TlsTestSupport.certificate(), response.handshake().peerCertificates().get(0));
                    return body.string();
                }
            });

            // Leave the server handshake pending so the provider remains registered for inspection.
            ALPN.ClientProvider provider = awaitProvider(sockets);
            assertEquals(List.of("h2", "http/1.1"), provider.protocols());
            String diagnostic = provider.toString();
            assertFalse(diagnostic.isBlank());
            assertEquals(diagnostic, provider.toString());

            assertEquals("GET /alpn-diagnostics HTTP/1.1", TlsTestSupport.serveRequest(server));
            assertEquals("secure", reply.get(30, TimeUnit.SECONDS));
            assertNull(ALPN.get(sockets.socket.get()));
        } finally {
            pool.evictAll();
        }
    }

    private static ALPN.ClientProvider awaitProvider(CapturingSocketFactory sockets) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            SSLSocket socket = sockets.socket.get();
            ALPN.Provider provider = socket == null ? null : ALPN.get(socket);
            if (provider != null) {
                return assertInstanceOf(ALPN.ClientProvider.class, provider);
            }
            Thread.sleep(10);
        }
        throw new AssertionError("HTTPS client did not register its ALPN provider");
    }

    private static final class CapturingSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final AtomicReference<SSLSocket> socket = new AtomicReference<>();

        private CapturingSocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket connected, String host, int port, boolean autoClose) throws IOException {
            SSLSocket created = (SSLSocket) delegate.createSocket(connected, host, port, autoClose);
            socket.set(created);
            return created;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return delegate.createSocket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return delegate.createSocket(host, port, localHost, localPort);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return delegate.createSocket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress host, int port, InetAddress localHost, int localPort) throws IOException {
            return delegate.createSocket(host, port, localHost, localPort);
        }
    }
}
