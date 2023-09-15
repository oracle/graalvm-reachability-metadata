/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk15on;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class BouncyCastleTest {

    @Test
    void testX509Certificate() throws Exception {
        Provider provider = new BouncyCastleProvider();

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", provider);
        keyPairGenerator.initialize(4096, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        JcaContentSignerBuilder contentSignerBuilder = new JcaContentSignerBuilder("SHA256WithRSAEncryption");
        ContentSigner contentSigner = contentSignerBuilder.build(keyPair.getPrivate());

        X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                new X500Name("CN=issuer.com"),
                BigInteger.valueOf(123),
                new Date(),
                new Date(System.currentTimeMillis() + 86400000), // 24 * 60 * 60 * 1000
                new X500Name("CN=subject.com"),
                keyPair.getPublic());

        X509CertificateHolder certificateHolder = certificateBuilder.build(contentSigner);

        JcaX509CertificateConverter certificateConverter = new JcaX509CertificateConverter();
        X509Certificate certificate = certificateConverter.setProvider(provider).getCertificate(certificateHolder);

        certificate.verify(keyPair.getPublic());

        assertThat("CN=issuer.com").isEqualTo(certificate.getIssuerX500Principal().getName());
        assertThat("CN=subject.com").isEqualTo(certificate.getSubjectX500Principal().getName());
        assertThat(123).isEqualTo(certificate.getSerialNumber().intValue());
    }
}
