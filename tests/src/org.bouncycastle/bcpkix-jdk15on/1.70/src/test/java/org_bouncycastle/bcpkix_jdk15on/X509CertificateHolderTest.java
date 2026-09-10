/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk15on;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.junit.jupiter.api.Test;

public interface X509CertificateHolderTest {

    @Test
    default void serializationRoundTripPreservesSignedCertificate() throws Exception {
        X500Name issuer = new X500Name("CN=Certificate Authority");
        X500Name subject = new X500Name("CN=Signed Service");
        BigInteger serialNumber = BigInteger.valueOf(7301);
        Date notBefore = Date.from(Instant.parse("2024-01-01T00:00:00Z"));
        Date notAfter = Date.from(Instant.parse("2034-01-01T00:00:00Z"));
        Provider provider = new BouncyCastleProvider();
        KeyPair keyPair = generateKeyPair(provider);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(provider)
                .build(keyPair.getPrivate());
        X509CertificateHolder original = new JcaX509v3CertificateBuilder(
                        issuer, serialNumber, notBefore, notAfter, subject, keyPair.getPublic())
                .build(signer);

        X509CertificateHolder restored = roundTrip(original);

        assertThat(restored).isEqualTo(original).isNotSameAs(original);
        assertThat(restored.getEncoded()).containsExactly(original.getEncoded());
        assertThat(restored.getSerialNumber()).isEqualTo(serialNumber);
        assertThat(restored.getIssuer()).isEqualTo(issuer);
        assertThat(restored.getSubject()).isEqualTo(subject);
        assertThat(restored.isSignatureValid(new JcaContentVerifierProviderBuilder()
                        .setProvider(provider)
                        .build(keyPair.getPublic())))
                .isTrue();
    }

    private static KeyPair generateKeyPair(Provider provider) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", provider);
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509CertificateHolder roundTrip(X509CertificateHolder certificate)
            throws Exception {
        ByteArrayOutputStream serialized = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(serialized)) {
            output.writeObject(certificate);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(serialized.toByteArray()))) {
            return (X509CertificateHolder) input.readObject();
        }
    }
}
