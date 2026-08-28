/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvRSAPublicKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesRestoresAndUsesAnRsaPublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "RSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAPublicKey generatedPublicKey = (RSAPublicKey) keyPair.getPublic();
        KeyFactory keyFactory = KeyFactory.getInstance(
            "RSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        PublicKey publicKey = keyFactory.generatePublic(new RSAPublicKeySpec(
            generatedPublicKey.getModulus(), generatedPublicKey.getPublicExponent()));

        PublicKey restoredPublicKey = deserialize(serialize(publicKey));

        assertThat(restoredPublicKey.getEncoded()).isEqualTo(publicKey.getEncoded());
        assertThat(verifySignature(restoredPublicKey, keyPair)).isTrue();
    }

    private byte[] serialize(PublicKey publicKey) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(publicKey);
        }
        return bytes.toByteArray();
    }

    private PublicKey deserialize(byte[] serializedKey) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(
            new ByteArrayInputStream(serializedKey))) {
            return (PublicKey) input.readObject();
        }
    }

    private boolean verifySignature(PublicKey publicKey, KeyPair keyPair) throws Exception {
        byte[] message = "serialized RSA public key".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance(
            "SHA256WITHRSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        signer.initSign(keyPair.getPrivate());
        signer.update(message);

        Signature verifier = Signature.getInstance(
            "SHA256WITHRSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        verifier.initVerify(publicKey);
        verifier.update(message);
        return verifier.verify(signer.sign());
    }
}
