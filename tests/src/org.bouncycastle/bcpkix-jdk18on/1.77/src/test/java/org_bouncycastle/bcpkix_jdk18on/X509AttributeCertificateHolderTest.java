/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk18on;

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
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.AttributeCertificateHolder;
import org.bouncycastle.cert.AttributeCertificateIssuer;
import org.bouncycastle.cert.X509AttributeCertificateHolder;
import org.bouncycastle.cert.X509v2AttributeCertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.junit.jupiter.api.Test;

public class X509AttributeCertificateHolderTest {

    private static final BigInteger SERIAL_NUMBER = BigInteger.valueOf(16384);
    private static final ASN1ObjectIdentifier ROLE_ATTRIBUTE =
            new ASN1ObjectIdentifier("1.3.6.1.5.5.7.10.4");
    private static final X500Name ISSUER = new X500Name("CN=Attribute Serialization Test CA");
    private static final X500Name SUBJECT = new X500Name("CN=Attribute Holder");
    private static final Date NOT_BEFORE = Date.from(Instant.parse("2024-01-01T00:00:00Z"));
    private static final Date NOT_AFTER = Date.from(Instant.parse("2034-01-01T00:00:00Z"));

    @Test
    void serializationRoundTripPreservesAttributeCertificate() throws Exception {
        Provider provider = new BouncyCastleProvider();
        KeyPair keyPair = generateKeyPair(provider);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(provider)
                .build(keyPair.getPrivate());
        X509v2AttributeCertificateBuilder builder = new X509v2AttributeCertificateBuilder(
                new AttributeCertificateHolder(SUBJECT),
                new AttributeCertificateIssuer(ISSUER),
                SERIAL_NUMBER,
                NOT_BEFORE,
                NOT_AFTER);
        builder.addAttribute(ROLE_ATTRIBUTE, new DERUTF8String("document-signer"));
        X509AttributeCertificateHolder original = builder.build(signer);

        X509AttributeCertificateHolder restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.getEncoded()).containsExactly(original.getEncoded());
        assertThat(restored.getSerialNumber()).isEqualTo(SERIAL_NUMBER);
        assertThat(restored.getHolder().getEntityNames()).containsExactly(SUBJECT);
        assertThat(restored.getIssuer().getNames()).containsExactly(ISSUER);
        assertThat(restored.getAttributes(ROLE_ATTRIBUTE)).hasSize(1);
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

    private static X509AttributeCertificateHolder roundTrip(
            X509AttributeCertificateHolder original) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (X509AttributeCertificateHolder) input.readObject();
        }
    }
}
