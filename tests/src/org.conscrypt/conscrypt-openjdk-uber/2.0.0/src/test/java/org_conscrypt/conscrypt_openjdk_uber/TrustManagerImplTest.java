/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;

import org.conscrypt.TrustManagerImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TrustManagerImplTest {

    private static final String CERTIFICATE_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIDjzCCAnegAwIBAgIUO5oHmmf+5qVbly17/26muNYUnzswDQYJKoZIhvcNAQEL
            BQAwVjELMAkGA1UEBhMCWFgxFTATBgNVBAcMDERlZmF1bHQgQ2l0eTEcMBoGA1UE
            CgwTRGVmYXVsdCBDb21wYW55IEx0ZDESMBAGA1UEAwwJbG9jYWxob3N0MCAXDTIy
            MDQyMTA3NDgwNFoYDzMwMjEwODIyMDc0ODA0WjBWMQswCQYDVQQGEwJYWDEVMBMG
            A1UEBwwMRGVmYXVsdCBDaXR5MRwwGgYDVQQKDBNEZWZhdWx0IENvbXBhbnkgTHRk
            MRIwEAYDVQQDDAlsb2NhbGhvc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK
            AoIBAQDAjRNfsO1LHH/1gZ9sx3YpEXRkA4EHQoUHC4yOn6qYLpR+Rg+rs69D/xUa
            7tHUNx0QVLMFPfpKNarNq1ftMlf5pyHQhME40+9fotKSh2o4QCiZmJwdQ54ZiDhk
            iIgSFrl/MJVcgyjNjg62/rnvDWbvy4FZvPxMKd2c1A4K/79/gY7N8utar1AKWDJJ
            esAB+09+8Z7utUaKBGSXoTXYe+JkZW2/AEeiJRP8PxUnyXJEzEm0vvh/NxHycCS/
            qswOIssbshyDDVw0PKJuEd3oGLn7fK7EFMc9/vR/H7RrSb08qaaLKghIvlwgm3ju
            mV4A7cr6bSQ6THKHibheeWfP7W57AgMBAAGjUzBRMB0GA1UdDgQWBBQ5oh+xJQMV
            d1Z5eukXsVTPmvmhuzAfBgNVHSMEGDAWgBQ5oh+xJQMVd1Z5eukXsVTPmvmhuzAP
            BgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQCHe/kqkToP6F48hfzn
            IpnQ8b6WSoFBeaxddK3EAF53p5WMG0siovn/dulpDmeR9zeajKRZoU/zgHck6tZM
            FgiCo7cD3YNjHvgAoCPSWZpU/cPy22fOTS66ht/LUEWzpeih4Zm3vRjUIgcaA93e
            RcUDXiyIgP7LOKNwe3R/EDHGmolMC7ZRLWne9P1O3fYcsvVR/aSUjwH0XiLj7D2P
            qFr1IxZ1LyAD0jtJ3+FCxDHsaxBO0nTUlKghGeEcl4woqR2fe9IPTxE3Zh7iRMxw
            pBdV7EvXeoeBENgavRrYjt+AIwl4FN/HGjW5hdZkP6ZYlMcFSa9WMpQZVfhQzMib
            WPBD
            -----END CERTIFICATE-----
            """;

    @Test
    void readsOcspAndSctSessionDataViaReflectionWhenSessionIsNotConscryptSession() throws Exception {
        X509Certificate certificate = parseCertificate(CERTIFICATE_PEM);
        TrustManagerImpl trustManager = createTrustManager(certificate);
        SSLSession session = new ReflectiveStatusSession(
                "localhost",
                List.of(new byte[] {0x01, 0x23, 0x45}),
                new byte[] {0x67, 0x45, 0x23, 0x01});

        List<X509Certificate> trustedChain = trustManager.checkServerTrusted(
                new X509Certificate[] {certificate},
                "RSA",
                session);

        assertThat(trustedChain).containsExactly(certificate);
    }

    private static TrustManagerImpl createTrustManager(X509Certificate certificate) throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("trusted-server", certificate);
        return new TrustManagerImpl(trustStore);
    }

    private static X509Certificate parseCertificate(String pem) throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII))) {
            return (X509Certificate) certificateFactory.generateCertificate(inputStream);
        }
    }

    private static final class ReflectiveStatusSession implements SSLSession {

        private final String peerHost;
        private final List<byte[]> statusResponses;
        private final byte[] signedCertificateTimestamp;
        private final Map<String, Object> values = new HashMap<>();

        private ReflectiveStatusSession(String peerHost, List<byte[]> statusResponses, byte[] signedCertificateTimestamp) {
            this.peerHost = peerHost;
            this.statusResponses = statusResponses;
            this.signedCertificateTimestamp = signedCertificateTimestamp;
        }

        public List<byte[]> getStatusResponses() {
            return statusResponses;
        }

        public byte[] getPeerSignedCertificateTimestamp() {
            return signedCertificateTimestamp;
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
            return 0;
        }

        @Override
        public long getLastAccessedTime() {
            return 0;
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
            values.put(name, value);
        }

        @Override
        public Object getValue(String name) {
            return values.get(name);
        }

        @Override
        public void removeValue(String name) {
            values.remove(name);
        }

        @Override
        public String[] getValueNames() {
            return values.keySet().toArray(String[]::new);
        }

        @Override
        public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
            return new Certificate[0];
        }

        @Override
        public Certificate[] getLocalCertificates() {
            return new Certificate[0];
        }

        @Override
        public javax.security.cert.X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
            return new javax.security.cert.X509Certificate[0];
        }

        @Override
        public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
            return null;
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
            return peerHost;
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
