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

    private static final ASN1ObjectIdentifier ACCESS_ROLE =
            new ASN1ObjectIdentifier("1.3.6.1.4.1.55555.1");
    private static final X500Name HOLDER_NAME = new X500Name("CN=Release Approver");
    private static final X500Name ISSUER_NAME = new X500Name("CN=Attribute Authority");
    private static final BigInteger SERIAL_NUMBER = BigInteger.valueOf(424242);
    private static final Date NOT_BEFORE = Date.from(Instant.parse("2024-01-01T00:00:00Z"));
    private static final Date NOT_AFTER = Date.from(Instant.parse("2034-01-01T00:00:00Z"));

    @Test
    void serializationRoundTripPreservesSignedAttributeCertificate() throws Exception {
        Provider provider = new BouncyCastleProvider();
        KeyPair keyPair = generateKeyPair(provider);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(provider)
                .build(keyPair.getPrivate());
        X509v2AttributeCertificateBuilder builder = new X509v2AttributeCertificateBuilder(
                new AttributeCertificateHolder(HOLDER_NAME),
                new AttributeCertificateIssuer(ISSUER_NAME),
                SERIAL_NUMBER,
                NOT_BEFORE,
                NOT_AFTER);
        builder.addAttribute(ACCESS_ROLE, new DERUTF8String("production-release"));
        X509AttributeCertificateHolder original = builder.build(signer);

        X509AttributeCertificateHolder restored = serializeAndRead(original);

        assertThat(restored).isEqualTo(original).isNotSameAs(original);
        assertThat(restored.getEncoded()).containsExactly(original.getEncoded());
        assertThat(restored.getSerialNumber()).isEqualTo(SERIAL_NUMBER);
        assertThat(restored.getHolder().getEntityNames()).containsExactly(HOLDER_NAME);
        assertThat(restored.getIssuer().getNames()).containsExactly(ISSUER_NAME);
        assertThat(restored.getAttributes(ACCESS_ROLE)).hasSize(1);
        assertThat(restored.isValidOn(Date.from(Instant.parse("2029-01-01T00:00:00Z")))).isTrue();
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

    private static X509AttributeCertificateHolder serializeAndRead(
            X509AttributeCertificateHolder certificate) throws Exception {
        ByteArrayOutputStream serialized = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(serialized)) {
            output.writeObject(certificate);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(serialized.toByteArray()))) {
            return (X509AttributeCertificateHolder) input.readObject();
        }
    }
}
