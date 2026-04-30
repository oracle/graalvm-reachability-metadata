/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.security.auth.x500.X500Principal;
import org.conscrypt.TrustManagerImpl;
import org.junit.jupiter.api.Test;

public class TrustManagerImplTest {
    private static final byte[] OCSP_RESPONSE = new byte[] {0x30, 0x03, 0x0A, 0x01, 0x00};
    private static final byte[] TLS_SCT_DATA = new byte[] {0x00, 0x01, 0x02, 0x03};

    @Test
    void serverTrustCheckReadsOpenJdkSessionStaplingData() throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        TrustManagerImpl trustManager = new TrustManagerImpl(trustStore);
        OpenJdkStyleSession session = new OpenJdkStyleSession();

        assertThrows(CertificateException.class,
                () -> trustManager.checkServerTrusted(
                        new X509Certificate[] {new UntrustedCertificate()}, "RSA", session));

        assertEquals(1, session.statusResponseCalls.get());
        assertEquals(1, session.tlsSctCalls.get());
        assertArrayEquals(OCSP_RESPONSE, session.lastStatusResponse);
        assertArrayEquals(TLS_SCT_DATA, session.lastTlsSctData);
    }

    public static final class OpenJdkStyleSession implements SSLSession {
        private final AtomicInteger statusResponseCalls = new AtomicInteger();
        private final AtomicInteger tlsSctCalls = new AtomicInteger();
        private byte[] lastStatusResponse;
        private byte[] lastTlsSctData;

        public List<byte[]> getStatusResponses() {
            statusResponseCalls.incrementAndGet();
            lastStatusResponse = OCSP_RESPONSE.clone();
            return List.of(lastStatusResponse);
        }

        public byte[] getPeerSignedCertificateTimestamp() {
            tlsSctCalls.incrementAndGet();
            lastTlsSctData = TLS_SCT_DATA.clone();
            return lastTlsSctData;
        }

        @Override
        public byte[] getId() {
            return new byte[] {1, 2, 3, 4};
        }

        @Override
        public SSLSessionContext getSessionContext() {
            return null;
        }

        @Override
        public long getCreationTime() {
            return 0L;
        }

        @Override
        public long getLastAccessedTime() {
            return 0L;
        }

        @Override
        public void invalidate() {
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void putValue(String name, Object value) {
        }

        @Override
        public Object getValue(String name) {
            return null;
        }

        @Override
        public void removeValue(String name) {
        }

        @Override
        public String[] getValueNames() {
            return new String[0];
        }

        @Override
        public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
            throw noPeerData("certificates");
        }

        @Override
        public Certificate[] getLocalCertificates() {
            return new Certificate[0];
        }

        @Override
        @SuppressWarnings("removal")
        public javax.security.cert.X509Certificate[] getPeerCertificateChain()
                throws SSLPeerUnverifiedException {
            throw noPeerData("certificate chain");
        }

        @Override
        public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
            throw noPeerData("principal");
        }

        @Override
        public Principal getLocalPrincipal() {
            return null;
        }

        @Override
        public String getCipherSuite() {
            return "TLS_AES_128_GCM_SHA256";
        }

        @Override
        public String getProtocol() {
            return "TLSv1.3";
        }

        @Override
        public String getPeerHost() {
            return "example.test";
        }

        @Override
        public int getPeerPort() {
            return 443;
        }

        @Override
        public int getPacketBufferSize() {
            return 16 * 1024;
        }

        @Override
        public int getApplicationBufferSize() {
            return 16 * 1024;
        }

        private static SSLPeerUnverifiedException noPeerData(String description) {
            return new SSLPeerUnverifiedException(
                    "No peer " + description + " is attached to this test session");
        }
    }

    private static final class UntrustedCertificate extends X509Certificate {
        private static final long serialVersionUID = 1L;
        private static final X500Principal SUBJECT = new X500Principal("CN=untrusted.example.test");
        private static final PublicKey PUBLIC_KEY = new SyntheticPublicKey();

        @Override
        public void checkValidity() {
        }

        @Override
        public void checkValidity(Date date) {
        }

        @Override
        public int getVersion() {
            return 3;
        }

        @Override
        public BigInteger getSerialNumber() {
            return BigInteger.ONE;
        }

        @Override
        public Principal getIssuerDN() {
            return SUBJECT;
        }

        @Override
        public Principal getSubjectDN() {
            return SUBJECT;
        }

        @Override
        public X500Principal getIssuerX500Principal() {
            return SUBJECT;
        }

        @Override
        public X500Principal getSubjectX500Principal() {
            return SUBJECT;
        }

        @Override
        public Date getNotBefore() {
            return new Date(0L);
        }

        @Override
        public Date getNotAfter() {
            return new Date(Long.MAX_VALUE);
        }

        @Override
        public byte[] getTBSCertificate() throws CertificateEncodingException {
            throw new CertificateEncodingException("Synthetic certificate has no encoded body");
        }

        @Override
        public byte[] getSignature() {
            return new byte[0];
        }

        @Override
        public String getSigAlgName() {
            return "SHA256withRSA";
        }

        @Override
        public String getSigAlgOID() {
            return "1.2.840.113549.1.1.11";
        }

        @Override
        public byte[] getSigAlgParams() {
            return new byte[0];
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            return null;
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            return null;
        }

        @Override
        public boolean[] getKeyUsage() {
            return null;
        }

        @Override
        public int getBasicConstraints() {
            return -1;
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
            throw new CertificateEncodingException("Synthetic certificate has no encoded form");
        }

        @Override
        public void verify(PublicKey key) throws CertificateException {
        }

        @Override
        public void verify(PublicKey key, String sigProvider) throws CertificateException {
        }

        @Override
        public String toString() {
            return "Synthetic untrusted certificate";
        }

        @Override
        public PublicKey getPublicKey() {
            return PUBLIC_KEY;
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            return false;
        }

        @Override
        public Set<String> getCriticalExtensionOIDs() {
            return Set.of();
        }

        @Override
        public Set<String> getNonCriticalExtensionOIDs() {
            return Set.of();
        }

        @Override
        public byte[] getExtensionValue(String oid) {
            return null;
        }

        private static final class SyntheticPublicKey implements PublicKey {
            private static final long serialVersionUID = 1L;

            @Override
            public String getAlgorithm() {
                return "RSA";
            }

            @Override
            public String getFormat() {
                return "X.509";
            }

            @Override
            public byte[] getEncoded() {
                return new byte[] {1, 2, 3, 4};
            }
        }
    }
}
