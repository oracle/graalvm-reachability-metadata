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
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
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

public class X509CertificateHolderTest {

    private static final BigInteger SERIAL_NUMBER = BigInteger.valueOf(8128);
    private static final X500Name ISSUER = new X500Name("CN=Serialization Test CA");
    private static final X500Name SUBJECT = new X500Name("CN=Certificate Holder");
    private static final Date NOT_BEFORE = Date.from(Instant.parse("2024-01-01T00:00:00Z"));
    private static final Date NOT_AFTER = Date.from(Instant.parse("2034-01-01T00:00:00Z"));

    @Test
    void serializationRoundTripPreservesCertificate() throws Exception {
        Provider provider = new BouncyCastleProvider();
        KeyPair keyPair = generateKeyPair(provider);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(provider)
                .build(keyPair.getPrivate());
        X509CertificateHolder original = new JcaX509v3CertificateBuilder(
                        ISSUER, SERIAL_NUMBER, NOT_BEFORE, NOT_AFTER, SUBJECT, keyPair.getPublic())
                .build(signer);

        X509CertificateHolder restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.getEncoded()).containsExactly(original.getEncoded());
        assertThat(restored.getSerialNumber()).isEqualTo(SERIAL_NUMBER);
        assertThat(restored.getIssuer()).isEqualTo(ISSUER);
        assertThat(restored.getSubject()).isEqualTo(SUBJECT);
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

    private static X509CertificateHolder roundTrip(X509CertificateHolder original) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new CertificateEncodingObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        try (ObjectInputStream input = new CertificateEncodingObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (X509CertificateHolder) input.readObject();
        }
    }

    // Give this holder's nested encoding write its own serialization descriptor path.
    private static final class CertificateEncodingObjectOutputStream extends ObjectOutputStream {

        private CertificateEncodingObjectOutputStream(OutputStream output) throws IOException {
            super(output);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) {
            if (!(object instanceof byte[])) {
                return object;
            }

            byte[] encoding = (byte[]) object;
            int[] replacement = new int[encoding.length];
            for (int index = 0; index < encoding.length; index++) {
                replacement[index] = encoding[index] & 0xff;
            }
            return replacement;
        }
    }

    private static final class CertificateEncodingObjectInputStream extends ObjectInputStream {

        private CertificateEncodingObjectInputStream(InputStream input) throws IOException {
            super(input);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) {
            if (!(object instanceof int[])) {
                return object;
            }

            int[] encoding = (int[]) object;
            byte[] replacement = new byte[encoding.length];
            for (int index = 0; index < encoding.length; index++) {
                replacement[index] = (byte) encoding[index];
            }
            return replacement;
        }
    }
}
