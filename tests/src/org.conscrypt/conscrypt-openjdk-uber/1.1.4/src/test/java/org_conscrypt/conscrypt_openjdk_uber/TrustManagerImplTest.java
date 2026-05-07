/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyStore;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSessionContext;
import org.conscrypt.TrustManagerImpl;
import org.junit.jupiter.api.Test;

public class TrustManagerImplTest {
    private static final String AUTH_TYPE = "RSA";
    private static final String PEER_HOST = "example.test";
    private static final byte[] OCSP_RESPONSE = new byte[] {1, 2, 3};
    private static final byte[] TLS_SCT_DATA = new byte[] {4, 5, 6};

    @Test
    void checkServerTrustedReadsOpenJdkSessionStapledStatusData() throws Exception {
        TrustManagerImpl trustManager = new TrustManagerImpl(emptyKeyStore());
        RecordingOpenJdkSession session = new RecordingOpenJdkSession();

        assertThatThrownBy(() -> trustManager.checkServerTrusted(
                new X509Certificate[0], AUTH_TYPE, session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or zero-length parameter");

        assertThat(session.statusResponsesCalls).isEqualTo(1);
        assertThat(session.peerSignedCertificateTimestampCalls).isEqualTo(1);
    }

    private static KeyStore emptyKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        return keyStore;
    }

    private static final class RecordingOpenJdkSession extends ExtendedSSLSession {
        private int statusResponsesCalls;
        private int peerSignedCertificateTimestampCalls;

        @Override
        public List<byte[]> getStatusResponses() {
            statusResponsesCalls++;
            return Collections.singletonList(OCSP_RESPONSE);
        }

        public byte[] getPeerSignedCertificateTimestamp() {
            peerSignedCertificateTimestampCalls++;
            return TLS_SCT_DATA.clone();
        }

        @Override
        public String[] getLocalSupportedSignatureAlgorithms() {
            return new String[0];
        }

        @Override
        public String[] getPeerSupportedSignatureAlgorithms() {
            return new String[0];
        }

        @Override
        public List<SNIServerName> getRequestedServerNames() {
            return Collections.emptyList();
        }

        @Override
        public byte[] getId() {
            return new byte[0];
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
            // Test session state is immutable.
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void putValue(String name, Object value) {
            // Test session does not store application values.
        }

        @Override
        public Object getValue(String name) {
            return null;
        }

        @Override
        public void removeValue(String name) {
            // Test session does not store application values.
        }

        @Override
        public String[] getValueNames() {
            return new String[0];
        }

        @Override
        public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
            throw new SSLPeerUnverifiedException("No peer certificates in test session");
        }

        @Override
        public Certificate[] getLocalCertificates() {
            return new Certificate[0];
        }

        @Override
        public javax.security.cert.X509Certificate[] getPeerCertificateChain()
                throws SSLPeerUnverifiedException {
            throw new SSLPeerUnverifiedException("No peer certificate chain in test session");
        }

        @Override
        public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
            throw new SSLPeerUnverifiedException("No peer principal in test session");
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
            return PEER_HOST;
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
    }
}
