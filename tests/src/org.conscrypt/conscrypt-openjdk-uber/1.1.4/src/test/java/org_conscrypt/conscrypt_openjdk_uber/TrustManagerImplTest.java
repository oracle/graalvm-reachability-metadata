/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.Principal;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import org.conscrypt.Conscrypt;
import org.conscrypt.TrustManagerImpl;
import org.junit.jupiter.api.Test;

public class TrustManagerImplTest {
    private static final byte[] OCSP_RESPONSE = new byte[] {0x30, 0x03, 0x0A, 0x01, 0x00};
    private static final byte[] TLS_SCT_DATA = new byte[] {0x00, 0x01, 0x02, 0x03};
    private static final String TRUSTED_CERTIFICATE_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIDJTCCAg2gAwIBAgIUJQRZp6VDDy9LsiXJjrRuq/uCKhowDQYJKoZIhvcNAQEL
            BQAwGTEXMBUGA1UEAwwOY29uc2NyeXB0LXRlc3QwIBcNMjYwNjI4MDUzMTE0WhgP
            MzAyNTEwMjkwNTMxMTRaMBkxFzAVBgNVBAMMDmNvbnNjcnlwdC10ZXN0MIIBIjAN
            BgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAs40jRfd/x3TpUgGqlEph6V2+3UK/
            V+5zMFI+Q1eJ7jTBYiAl4/vFlHS3UvYTNJmrL0zVhAsIYRe+HfXkHHiREX57PaVA
            G+vx3FMEafDewp2MtWoNmRQ+Kgh9XVn8VOC2mRqVrsY48SLnqdkjDV99X8kv0Y92
            enfLzAKwvIRXy7TTlvbgo5fyX3oW1PdHaOnldl4H3nVdUb/tK3dBPm2X8GYppzeT
            LT5A8cBhIPX+scYABctzjWztAE7FCYpnDePZAP1tzEkoek0l86jKV/bozDiTd7Yy
            yXVOVmALaU8Qbre9VFphWgTvfHZy+wLszkN5UmxpIDoEbf4Ra78muRco1QIDAQAB
            o2MwYTAdBgNVHQ4EFgQUAmIoT7gQbz1Zkf9a4QEWp2TczEEwHwYDVR0jBBgwFoAU
            AmIoT7gQbz1Zkf9a4QEWp2TczEEwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8E
            BAMCAqQwDQYJKoZIhvcNAQELBQADggEBABOe5iq9oyox+JheGVWG9rpdfaGjBVQu
            YqG/TbKb3SmzR65nziHNiyP6Qwr4vR1T5ksK68AKGCg+vDWt7p6QfYZG1wdRhpFJ
            FYdWXef62QQLgchVRH3pbJ2hXHXCh+o6KITGz1B70oNHXM1LIBshQ10+Sc//2NaZ
            CYBJrB03xt1sE2n3xomssp2+8Y7mSNK2Jl40Ymwb2bjeDMwkvAA87W/rP3mbtzF1
            oYFIEkbksVZSpF4GZ0Bo9CSNIP8vWTl0epCbQu3eZg8FiM5d3iV3nJoVE3uGocWK
            wUnWhJfFTe947JGBxyJ58QKVtF9WVQBXViDprOuydahZ75u2iF7nPq0=
            -----END CERTIFICATE-----
            """;

    @Test
    void checkServerTrustedReadsStapledDataFromDuckTypedSession() throws Exception {
        Conscrypt.checkAvailability();
        Provider provider = Conscrypt.newProvider();
        assertThat(provider.getName()).isNotBlank();
        X509Certificate trustedCertificate = trustedCertificate();
        TrustManagerImpl trustManager = new TrustManagerImpl(trustStore(trustedCertificate));
        StapledDataSession session = new StapledDataSession();

        List<X509Certificate> trustedChain = trustManager.checkServerTrusted(
                new X509Certificate[] {trustedCertificate}, "RSA", session);

        assertThat(trustedChain).containsExactly(trustedCertificate);
        assertThat(session.statusResponsesCallCount).isEqualTo(1);
        assertThat(session.tlsSctDataCallCount).isEqualTo(1);
    }

    private static X509Certificate trustedCertificate() throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        byte[] pemBytes = TRUSTED_CERTIFICATE_PEM.getBytes(StandardCharsets.US_ASCII);
        Certificate certificate = factory.generateCertificate(new ByteArrayInputStream(pemBytes));
        assertThat(certificate).isInstanceOf(X509Certificate.class);
        return (X509Certificate) certificate;
    }

    private static KeyStore trustStore(X509Certificate trustedCertificate) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("trusted", trustedCertificate);
        return keyStore;
    }

    @SuppressWarnings("removal")
    private static final class StapledDataSession implements SSLSession {
        private int statusResponsesCallCount;
        private int tlsSctDataCallCount;

        public List<byte[]> getStatusResponses() {
            statusResponsesCallCount++;
            return List.of(OCSP_RESPONSE.clone());
        }

        public byte[] getPeerSignedCertificateTimestamp() {
            tlsSctDataCallCount++;
            return TLS_SCT_DATA.clone();
        }

        @Override
        public byte[] getId() {
            return new byte[] {1, 2, 3};
        }

        @Override
        public SSLSessionContext getSessionContext() {
            return null;
        }

        @Override
        public long getCreationTime() {
            return 1L;
        }

        @Override
        public long getLastAccessedTime() {
            return 1L;
        }

        @Override
        public void invalidate() {
            throw new UnsupportedOperationException("Session invalidation is not used");
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void putValue(String name, Object value) {
            throw new UnsupportedOperationException("Session values are not used");
        }

        @Override
        public Object getValue(String name) {
            return null;
        }

        @Override
        public void removeValue(String name) {
            throw new UnsupportedOperationException("Session values are not used");
        }

        @Override
        public String[] getValueNames() {
            return new String[0];
        }

        @Override
        public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
            throw new SSLPeerUnverifiedException("No peer certificates");
        }

        @Override
        public Certificate[] getLocalCertificates() {
            return new Certificate[0];
        }

        @Override
        public javax.security.cert.X509Certificate[] getPeerCertificateChain()
                throws SSLPeerUnverifiedException {
            throw new SSLPeerUnverifiedException("No peer certificate chain");
        }

        @Override
        public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
            throw new SSLPeerUnverifiedException("No peer principal");
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
            return "conscrypt-test.example";
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
