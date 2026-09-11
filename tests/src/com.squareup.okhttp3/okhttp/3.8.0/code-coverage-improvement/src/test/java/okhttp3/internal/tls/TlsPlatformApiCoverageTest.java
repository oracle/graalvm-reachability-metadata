/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package okhttp3.internal.tls;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import okhttp3.CertificatePinner;
import okio.ByteString;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509TrustManager;
import okhttp3.Protocol;
import okhttp3.internal.platform.Platform;
import org.junit.jupiter.api.Test;

public class TlsPlatformApiCoverageTest {
    @Test
    void platformHandlesTlsSocketsAndLocalConnections() throws Exception {
        Platform platform = Platform.get();
        assertThat(platform.getPrefix()).isNotNull();
        SSLSocket socket = (SSLSocket) SSLContext.getDefault().getSocketFactory().createSocket();
        platform.configureTlsExtensions(socket, "example.com", Collections.singletonList(Protocol.HTTP_2));
        assertThat(platform.getSelectedProtocol(socket)).isNull();

        Platform defaultPlatform = new Platform();
        defaultPlatform.configureTlsExtensions(socket, "example.com",
                Collections.singletonList(Protocol.HTTP_1_1));
        assertThat(defaultPlatform.getSelectedProtocol(socket)).isNull();
        defaultPlatform.afterHandshake(socket);
        platform.afterHandshake(socket);
        platform.log(Platform.INFO, "coverage", null);
        platform.logCloseableLeak("coverage", platform.getStackTraceForCloseable("coverage"));
        socket.close();

        try (ServerSocket server = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try {
                    server.accept().close();
                } catch (java.io.IOException ignored) {
                }
            });
            acceptor.start();
            java.net.Socket client = new java.net.Socket();
            platform.connectSocket(client, new InetSocketAddress("localhost", server.getLocalPort()), 1000);
            assertThat(client.isConnected()).isTrue();
            client.close();
            acceptor.join(1000L);
        }
        X509TrustManager trustManager = new EmptyTrustManager();
        assertThat(platform.buildCertificateChainCleaner(trustManager)).isNotNull();
        assertThat(CertificateChainCleaner.get(trustManager)).isNotNull();
        assertThat(CertificateChainCleaner.get(new X509Certificate[0])).isNotNull();
    }

    @Test
    void hostnameVerificationAndCertificateChainsUsePublicContracts() throws Exception {
        X509Certificate certificate = new SubjectCertificate();
        assertThat(OkHostnameVerifier.allSubjectAltNames(certificate))
                .containsExactlyInAnyOrder("example.com", "127.0.0.1");
        assertThat(OkHostnameVerifier.INSTANCE.verifyHostname("example.com", "example.com"))
                .isTrue();
        assertThat(OkHostnameVerifier.INSTANCE.verifyHostname("a.example.com", "*.example.com"))
                .isTrue();
        assertThat(OkHostnameVerifier.INSTANCE.verify("example.com", certificate)).isTrue();
        assertThat(OkHostnameVerifier.INSTANCE.verify("127.0.0.1", certificate)).isTrue();
        assertThat(OkHostnameVerifier.INSTANCE.verify("other.example", certificate)).isFalse();
        assertThat(OkHostnameVerifier.INSTANCE.verify("example.com",
                new SubjectCertificate.SubjectOnlyCertificate("OU=Ops\\,North,CN=example.com"))).isTrue();
        assertThat(OkHostnameVerifier.INSTANCE.verify("example.com",
                new SubjectCertificate.SubjectOnlyCertificate("OU=#13034f7073,CN=example.com"))).isTrue();
        assertThat(OkHostnameVerifier.INSTANCE.verify("hex.example.com",
                new SubjectCertificate.SubjectOnlyCertificate("CN=#13034f7073"))).isFalse();
        assertThat(OkHostnameVerifier.INSTANCE.verify("example.com",
                new SubjectCertificate.SubjectOnlyCertificate("CN=\\C3\\A9xample.com"))).isFalse();
        assertThat(OkHostnameVerifier.INSTANCE.verify("\u00e9xample.com",
                new SubjectCertificate.SubjectOnlyCertificate("CN=\\C3\\A9xample.com"))).isTrue();
        assertThat(OkHostnameVerifier.INSTANCE.verify("quoted.example",
                new SubjectCertificate.SubjectOnlyCertificate("CN=\"quoted.example\""))).isTrue();
        assertThat(OkHostnameVerifier.INSTANCE.verify("example.com",
                new SubjectCertificate.SubjectOnlyCertificate("CN=\"example.com\""))).isTrue();
        assertThat(OkHostnameVerifier.INSTANCE.verify("example.com",
                new SubjectCertificate.SubjectOnlyCertificate("CN=#130B6578616D706C652E636F6D"))).isTrue();
        ByteString publicKey = ByteString.of(certificate.getPublicKey().getEncoded());
        String sha1Pin = "sha1/" + publicKey.sha1().base64();
        String sha256Pin = "sha256/" + publicKey.sha256().base64();
        new CertificatePinner.Builder().add("example.com", sha1Pin).build()
                .check("example.com", certificate);
        new CertificatePinner.Builder().add("example.com", sha256Pin).build()
                .check("example.com", certificate);

        TrustRootIndex roots = new TrustRootIndex() {
            public X509Certificate findByIssuerAndSignature(X509Certificate candidate) {
                return certificate;
            }
        };
        javax.net.ssl.TrustManagerFactory trustManagers =
                javax.net.ssl.TrustManagerFactory.getInstance(
                        javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init((java.security.KeyStore) null);
        X509Certificate trustedCertificate = ((X509TrustManager) trustManagers.getTrustManagers()[0])
                .getAcceptedIssuers()[0];
        TrustRootIndex basicRoots = TrustRootIndex.get(trustedCertificate);
        basicRoots.findByIssuerAndSignature(trustedCertificate);
        BasicCertificateChainCleaner first = new BasicCertificateChainCleaner(roots);
        BasicCertificateChainCleaner second = new BasicCertificateChainCleaner(roots);
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first.clean(Collections.<java.security.cert.Certificate>singletonList(certificate),
                "example.com")).containsExactly(certificate);

    }

    private static final class EmptyTrustManager implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private static class SubjectCertificate extends X509Certificate {
        private static final PublicKey PUBLIC_KEY = new PublicKey() {
            public String getAlgorithm() {
                return "RSA";
            }

            public String getFormat() {
                return "X.509";
            }

            public byte[] getEncoded() {
                return new byte[] {1, 2, 3};
            }
        };

        public Collection<List<?>> getSubjectAlternativeNames() throws CertificateParsingException {
            return Arrays.<List<?>>asList(Arrays.<Object>asList(2, "example.com"),
                    Arrays.<Object>asList(7, "127.0.0.1"));
        }

        public void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException {
        }

        public void checkValidity(Date date)
                throws CertificateExpiredException, CertificateNotYetValidException {
        }

        public int getVersion() {
            return 3;
        }

        public BigInteger getSerialNumber() {
            return BigInteger.ONE;
        }

        public Principal getIssuerDN() {
            return new X500Principal("CN=example");
        }

        public Principal getSubjectDN() {
            return new X500Principal("CN=example");
        }

        private static final class SubjectOnlyCertificate extends SubjectCertificate {
            private final X500Principal principal;

            private SubjectOnlyCertificate(String distinguishedName) {
                principal = new X500Principal(distinguishedName);
            }

            @Override public Collection<List<?>> getSubjectAlternativeNames() {
                return null;
            }

            @Override public X500Principal getSubjectX500Principal() {
                return principal;
            }
        }

        public Date getNotBefore() {
            return new Date(0L);
        }

        public Date getNotAfter() {
            return new Date(Long.MAX_VALUE);
        }

        public byte[] getTBSCertificate() throws CertificateEncodingException {
            return new byte[0];
        }

        public byte[] getSignature() {
            return new byte[0];
        }

        public String getSigAlgName() {
            return "none";
        }

        public String getSigAlgOID() {
            return "0.0";
        }

        public byte[] getSigAlgParams() {
            return null;
        }

        public boolean[] getIssuerUniqueID() {
            return null;
        }

        public boolean[] getSubjectUniqueID() {
            return null;
        }

        public boolean[] getKeyUsage() {
            return null;
        }

        public int getBasicConstraints() {
            return -1;
        }

        public byte[] getEncoded() {
            return new byte[0];
        }

        public void verify(PublicKey key) {
        }

        public void verify(PublicKey key, String sigProvider) {
        }

        public String toString() {
            return "subject certificate";
        }

        public PublicKey getPublicKey() {
            return PUBLIC_KEY;
        }

        public boolean hasUnsupportedCriticalExtension() {
            return false;
        }

        public Set<String> getCriticalExtensionOIDs() {
            return null;
        }

        public Set<String> getNonCriticalExtensionOIDs() {
            return null;
        }

        public byte[] getExtensionValue(String oid) {
            return null;
        }
    }
}
