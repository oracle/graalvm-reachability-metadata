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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvEdDSAPublicKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesRestoresAndUsesAnEd25519PublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "Ed25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        KeyPair keyPair = generator.generateKeyPair();
        PublicKey restoredPublicKey = deserialize(serialize(keyPair.getPublic()));

        assertThat(restoredPublicKey.getEncoded())
            .isEqualTo(keyPair.getPublic().getEncoded());
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
        byte[] message = "serialized Ed25519 public key".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance(
            "Ed25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        signer.initSign(keyPair.getPrivate());
        signer.update(message);

        Signature verifier = Signature.getInstance(
            "Ed25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        verifier.initVerify(publicKey);
        verifier.update(message);
        return verifier.verify(signer.sign());
    }
}
