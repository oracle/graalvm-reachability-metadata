/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk15to18;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class BouncyCastleTest {

    private final Provider provider = new BouncyCastleProvider();

    @Test
    void testX509Certificate() throws Exception {
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

        assertThat(certificate.getIssuerX500Principal().getName()).isEqualTo("CN=issuer.com");
        assertThat(certificate.getSubjectX500Principal().getName()).isEqualTo("CN=subject.com");
        assertThat(certificate.getSerialNumber().intValue()).isEqualTo(123);
    }

    @Test
    void testReadWritePrivatePemPKCS1() throws Exception {
        testReadWritePrivatePem(true);
    }

    @Test
    void testReadWritePrivatePemPKCS8() throws Exception {
        testReadWritePrivatePem(false);
    }

    private void testReadWritePrivatePem(boolean isPKCS1) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", provider);
        keyPairGenerator.initialize(4096, new SecureRandom());
        KeyPair generatedKeyPair = keyPairGenerator.generateKeyPair();

        String pemType = PEMParser.TYPE_PRIVATE_KEY;
        byte[] privateKeyBytes = generatedKeyPair.getPrivate().getEncoded();
        if (isPKCS1) {
            pemType = PEMParser.TYPE_RSA_PRIVATE_KEY;
            privateKeyBytes = PrivateKeyInfo.getInstance(privateKeyBytes).parsePrivateKey().toASN1Primitive().getEncoded();
        }

        PemObject pemObject = new PemObject(pemType, privateKeyBytes);

        ByteArrayOutputStream bytesOutputStream = new ByteArrayOutputStream();
        try (PemWriter pemWriter = new PemWriter(new OutputStreamWriter(bytesOutputStream))) {
            pemWriter.writeObject(pemObject);
        }

        String privatePem = bytesOutputStream.toString();
        if (isPKCS1) {
            assertThat(privatePem).startsWith("-----BEGIN RSA PRIVATE KEY-----");
        } else {
            assertThat(privatePem).startsWith("-----BEGIN PRIVATE KEY-----");
        }

        Object keyObject;
        try (PEMParser pemParser = new PEMParser(new InputStreamReader(new ByteArrayInputStream(bytesOutputStream.toByteArray())))) {
            keyObject = pemParser.readObject();
        }

        JcaPEMKeyConverter keyConverter = new JcaPEMKeyConverter().setProvider(provider);

        PrivateKey privateKey;
        if (isPKCS1) {
            privateKey = keyConverter.getKeyPair((PEMKeyPair) keyObject).getPrivate();
        } else {
            privateKey = keyConverter.getPrivateKey((PrivateKeyInfo) keyObject);
        }

        assertThat(privateKey.toString()).startsWith("RSA Private CRT Key");
    }
}
