/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

final class TlsContexts {
    private TlsContexts() {
    }

    static SSLContext create() throws Exception {
        Certificate certificate;
        try (InputStream input = TlsContexts.class.getResourceAsStream("/localhost-certificate.pem")) {
            certificate = CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        String encodedKey;
        try (InputStream input = TlsContexts.class.getResourceAsStream("/localhost-private-key.pem")) {
            encodedKey = new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
        }
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encodedKey)));
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        char[] password = "local-test-only".toCharArray();
        store.setKeyEntry("localhost", key, password, new Certificate[]{certificate});
        store.setCertificateEntry("trusted-localhost", certificate);
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, password);
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
        return context;
    }
}
