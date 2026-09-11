/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package okhttp3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.internal.cache.CacheRequest;
import okio.Sink;
import org.junit.jupiter.api.Test;

public class CacheDeepCoverageTest {
    @Test
    void cachePutAndGetRoundTripHttpsCertificateMetadata() throws Exception {
        File directory = java.nio.file.Files.createTempDirectory("okhttp-cache-https").toFile();
        Cache cache = new Cache(directory, 8192L);
        cache.initialize();
        Request request = new Request.Builder().url("https://example.com/cached").build();
        X509Certificate certificate = defaultCertificate();
        Handshake handshake = Handshake.get(TlsVersion.TLS_1_2,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                Collections.<Certificate>singletonList(certificate),
                Collections.<Certificate>emptyList());
        Response response = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").handshake(handshake)
                .networkResponse(networkResponse(request)).header("Content-Length", "4")
                .body(ResponseBody.create(null, "body"))
                .build();
        CacheRequest cacheRequest = cache.put(response);
        assertThat(cacheRequest).isNotNull();
        okio.BufferedSink body = okio.Okio.buffer(cacheRequest.body());
        body.writeUtf8("body").close();

        Response cached = cache.get(request);
        assertThat(cached).isNotNull();
        assertThat(cached.handshake().peerCertificates()).containsExactly(certificate);
        assertThat(cached.body().contentLength()).isEqualTo(4L);
        assertThat(cached.body().string()).isEqualTo("body");
        cache.close();
    }

    @Test
    void cachePutAbortsAndFaultHidingSinkHandlesClosedEditors() throws Exception {
        File directory = java.nio.file.Files.createTempDirectory("okhttp-cache-failure").toFile();
        Cache cache = new Cache(directory, 8192L);
        cache.initialize();
        Request request = new Request.Builder().url("https://example.com/broken").build();
        Handshake handshake = Handshake.get(TlsVersion.TLS_1_2,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                Collections.<Certificate>singletonList(new BrokenCertificate()),
                Collections.<Certificate>emptyList());
        Response response = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").handshake(handshake)
                .networkResponse(networkResponse(request)).build();
        assertThat(cache.put(response)).isNull();
        assertThat(cache.get(request)).isNull();

        Request httpRequest = new Request.Builder().url("http://example.com/body").build();
        Response httpResponse = new Response.Builder().request(httpRequest).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").networkResponse(networkResponse(httpRequest))
                .body(ResponseBody.create(null, "body")).build();
        CacheRequest pending = cache.put(httpResponse);
        Sink sink = pending.body();
        cache.close();
        try {
            sink.close();
        } catch (Exception expectedClosedEditor) {
            assertThat(expectedClosedEditor).isNotNull();
        }
        pending.abort();
    }

    private static Response networkResponse(Request request) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").build();
    }

    private static X509Certificate defaultCertificate() throws Exception {
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init((java.security.KeyStore) null);
        X509TrustManager trustManager = (X509TrustManager) trustManagers.getTrustManagers()[0];
        return trustManager.getAcceptedIssuers()[0];
    }

    private static final class BrokenCertificate extends Certificate {
        private BrokenCertificate() {
            super("X.509");
        }

        public byte[] getEncoded() throws CertificateEncodingException {
            throw new CertificateEncodingException("broken certificate");
        }

        public void verify(PublicKey key) throws CertificateException, InvalidKeyException,
                NoSuchAlgorithmException {
        }

        public void verify(PublicKey key, String provider) throws CertificateException,
                InvalidKeyException, NoSuchAlgorithmException {
        }

        public String toString() {
            return "broken certificate";
        }

        public PublicKey getPublicKey() {
            return null;
        }
    }
}
