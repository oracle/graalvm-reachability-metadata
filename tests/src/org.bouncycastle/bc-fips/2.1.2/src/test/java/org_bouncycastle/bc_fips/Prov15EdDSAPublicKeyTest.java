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
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Prov15EdDSAPublicKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesAndRestoresAnEd25519PublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "Ed25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        KeyPair keyPair = generator.generateKeyPair();
        KeyFactory keyFactory = KeyFactory.getInstance(
            "Ed25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        PublicKey publicKey = keyFactory.generatePublic(
            new X509EncodedKeySpec(keyPair.getPublic().getEncoded()));
        PublicKey restoredPublicKey = deserialize(serialize(publicKey));
        PublicKey restoredUnsharedPublicKey = deserializeUnshared(serializeUnshared(publicKey));

        assertThat(restoredPublicKey.getAlgorithm()).isEqualTo("Ed25519");
        assertThat(restoredPublicKey.getEncoded()).isEqualTo(publicKey.getEncoded());
        assertThat(restoredUnsharedPublicKey.getEncoded()).isEqualTo(publicKey.getEncoded());
        assertThat(verifiesSignature(keyPair, restoredPublicKey, "serialized public key")).isTrue();
    }

    private byte[] serialize(PublicKey publicKey) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(publicKey);
        }
        return bytes.toByteArray();
    }

    private byte[] serializeUnshared(PublicKey publicKey) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeUnshared(publicKey);
        }
        return bytes.toByteArray();
    }

    private PublicKey deserialize(byte[] serializedPublicKey) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedPublicKey))) {
            return (PublicKey) input.readObject();
        }
    }

    private PublicKey deserializeUnshared(byte[] serializedPublicKey) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedPublicKey))) {
            return (PublicKey) input.readUnshared();
        }
    }

    private boolean verifiesSignature(KeyPair keyPair, PublicKey publicKey, String message)
        throws Exception {
        byte[] messageBytes = message.getBytes();
        Signature signer = Signature.getInstance("Ed25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        signer.initSign(keyPair.getPrivate());
        signer.update(messageBytes);

        Signature verifier = Signature.getInstance("Ed25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        verifier.initVerify(publicKey);
        verifier.update(messageBytes);
        return verifier.verify(signer.sign());
    }
}
