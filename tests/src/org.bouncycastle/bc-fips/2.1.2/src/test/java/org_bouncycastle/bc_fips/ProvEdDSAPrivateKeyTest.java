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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvEdDSAPrivateKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesRestoresAndUsesAnEd25519PrivateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "Ed25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        KeyPair keyPair = generator.generateKeyPair();
        KeyFactory keyFactory = KeyFactory.getInstance(
            "Ed25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        PrivateKey privateKey = keyFactory.generatePrivate(
            new PKCS8EncodedKeySpec(keyPair.getPrivate().getEncoded()));

        PrivateKey restoredPrivateKey = deserialize(serialize(privateKey));

        assertThat(restoredPrivateKey.getAlgorithm()).isEqualTo("Ed25519");
        assertThat(restoredPrivateKey.getEncoded()).isEqualTo(privateKey.getEncoded());
        assertThat(signAndVerify(restoredPrivateKey, keyPair.getPublic())).isTrue();
    }

    @Test
    void serializesAnEd25519KeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "Ed25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        KeyPair keyPair = generator.generateKeyPair();

        KeyPair restoredKeyPair = deserializeKeyPair(serializeKeyPair(keyPair));

        assertThat(restoredKeyPair.getPrivate().getEncoded())
            .isEqualTo(keyPair.getPrivate().getEncoded());
        assertThat(signAndVerify(restoredKeyPair.getPrivate(), restoredKeyPair.getPublic())).isTrue();
    }

    private byte[] serialize(PrivateKey privateKey) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(privateKey);
        }
        return bytes.toByteArray();
    }

    private PrivateKey deserialize(byte[] serializedPrivateKey) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(
            new ByteArrayInputStream(serializedPrivateKey))) {
            return (PrivateKey) input.readObject();
        }
    }

    private byte[] serializeKeyPair(KeyPair keyPair) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeUnshared(keyPair);
        }
        return bytes.toByteArray();
    }

    private KeyPair deserializeKeyPair(byte[] serializedKeyPair) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(
            new ByteArrayInputStream(serializedKeyPair))) {
            return (KeyPair) input.readUnshared();
        }
    }

    private boolean signAndVerify(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        byte[] message = "serialized Ed25519 private key".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("Ed25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        signer.initSign(privateKey);
        signer.update(message);

        Signature verifier = Signature.getInstance("Ed25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        verifier.initVerify(publicKey);
        verifier.update(message);
        return verifier.verify(signer.sign());
    }
}
