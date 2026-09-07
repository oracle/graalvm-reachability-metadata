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
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.junit.jupiter.api.Test;

public class X509CRLHolderTest {

    private static final BigInteger REVOKED_SERIAL_NUMBER = BigInteger.valueOf(4096);
    private static final X500Name ISSUER = new X500Name("CN=CRL Serialization Test CA");
    private static final Date THIS_UPDATE = Date.from(Instant.parse("2024-01-01T00:00:00Z"));
    private static final Date NEXT_UPDATE = Date.from(Instant.parse("2025-01-01T00:00:00Z"));
    private static final Date REVOCATION_DATE = Date.from(Instant.parse("2024-02-01T00:00:00Z"));

    @Test
    void serializationRoundTripPreservesRevocationList() throws Exception {
        Provider provider = new BouncyCastleProvider();
        KeyPair keyPair = generateKeyPair(provider);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(provider)
                .build(keyPair.getPrivate());
        JcaX509v2CRLBuilder builder = new JcaX509v2CRLBuilder(
                new X500Principal(ISSUER.getEncoded()), THIS_UPDATE);
        builder.setNextUpdate(NEXT_UPDATE);
        builder.addCRLEntry(REVOKED_SERIAL_NUMBER, REVOCATION_DATE, CRLReason.keyCompromise);
        X509CRLHolder original = builder.build(signer);

        X509CRLHolder restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.getEncoded()).containsExactly(original.getEncoded());
        assertThat(restored.getIssuer()).isEqualTo(ISSUER);
        assertThat(restored.getRevokedCertificate(REVOKED_SERIAL_NUMBER).getSerialNumber())
                .isEqualTo(REVOKED_SERIAL_NUMBER);
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

    private static X509CRLHolder roundTrip(X509CRLHolder original) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (X509CRLHolder) input.readObject();
        }
    }
}
