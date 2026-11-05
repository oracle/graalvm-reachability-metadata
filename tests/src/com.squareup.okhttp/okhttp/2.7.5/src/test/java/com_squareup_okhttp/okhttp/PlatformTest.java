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
import javax.net.ssl.SSLServerSocket;

import com.squareup.okhttp.CertificatePinner;
import com.squareup.okhttp.ConnectionPool;
import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.TlsVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PlatformTest {
    @Test
    @Timeout(60)
    public void verifiesPinnedCertificateWithTheSocketFactoryTrustRoots() throws Exception {
        SSLContext context = TlsTestSupport.context();
        ConnectionPool pool = new ConnectionPool(0, 10_000);
        try (SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
                ExecutorService executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(10_000);
            server.setEnabledProtocols(new String[]{"TLSv1.2"});
            Future<String> requestLine = executor.submit(() -> TlsTestSupport.serveRequest(server));
            CertificatePinner pinner = new CertificatePinner.Builder()
                    .add("127.0.0.1", CertificatePinner.pin(TlsTestSupport.certificate()))
                    .build();
            ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .build();
            OkHttpClient client = new OkHttpClient()
                    .setSslSocketFactory(context.getSocketFactory())
                    .setCertificatePinner(pinner)
                    .setProxy(Proxy.NO_PROXY)
                    .setConnectionPool(pool)
                    .setConnectionSpecs(List.of(spec))
                    .setProtocols(List.of(Protocol.HTTP_1_1));
            client.setConnectTimeout(10, TimeUnit.SECONDS);
            client.setReadTimeout(10, TimeUnit.SECONDS);
            client.setWriteTimeout(10, TimeUnit.SECONDS);
            Request request = new Request.Builder()
                    .url("https://127.0.0.1:" + server.getLocalPort() + "/pinned")
                    .build();
            Response response = client.newCall(request).execute();
            try (ResponseBody body = response.body()) {
                assertEquals(200, response.code());
                assertEquals("secure", body.string());
                assertEquals(TlsTestSupport.certificate(), response.handshake().peerCertificates().get(0));
            }
            assertEquals("GET /pinned HTTP/1.1", requestLine.get(20, TimeUnit.SECONDS));
        } finally {
            pool.evictAll();
        }
    }
}
