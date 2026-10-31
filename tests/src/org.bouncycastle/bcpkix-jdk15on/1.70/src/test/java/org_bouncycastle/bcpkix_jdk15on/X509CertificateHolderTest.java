/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk15on;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

public class X509CertificateHolderTest {

    @Test
    void serializationPreservesSignedCertificateAndExtensions() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        X500Name issuer = new X500Name("CN=Certificate Authority");
        X500Name subject = new X500Name("CN=service.example");
        BigInteger serial = BigInteger.valueOf(314);
        Date notBefore = new Date(1704067200000L);
        Date notAfter = new Date(1735689600000L);
        GeneralName alternativeName = new GeneralName(GeneralName.dNSName, "service.example");

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(alternativeName));
        X509CertificateHolder original = builder.build(
                new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate()));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        X509CertificateHolder restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (X509CertificateHolder) input.readObject();
        }

        assertThat(restored).isNotSameAs(original).isEqualTo(original);
        assertThat(restored.getEncoded()).isEqualTo(original.getEncoded());
        assertThat(restored.getSerialNumber()).isEqualTo(serial);
        assertThat(restored.getIssuer()).isEqualTo(issuer);
        assertThat(restored.getSubject()).isEqualTo(subject);
        assertThat(restored.getNotBefore()).isEqualTo(notBefore);
        assertThat(restored.getNotAfter()).isEqualTo(notAfter);
        assertThat(restored.isValidOn(new Date(1719792000000L))).isTrue();
        assertThat(restored.isValidOn(new Date(1735776000000L))).isFalse();
        assertThat(restored.getSubjectPublicKeyInfo().getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
        assertThat(restored.hasExtensions()).isTrue();
        assertThat(restored.getExtension(Extension.basicConstraints).isCritical()).isTrue();
        assertThat(BasicConstraints.fromExtensions(restored.getExtensions()).isCA()).isFalse();
        assertThat(GeneralNames.fromExtensions(restored.getExtensions(), Extension.subjectAlternativeName).getNames())
                .containsExactly(alternativeName);
        assertThat(restored.isSignatureValid(
                new JcaContentVerifierProviderBuilder().build(keyPair.getPublic()))).isTrue();
    }
}
