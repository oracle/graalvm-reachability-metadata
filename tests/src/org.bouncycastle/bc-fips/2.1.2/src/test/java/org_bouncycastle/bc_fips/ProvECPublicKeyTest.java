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
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPublicKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvECPublicKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesRestoresAndUsesAnEcPublicKey() throws Exception {
        KeyPair keyPair = generateEcKeyPair();
        PublicKey publicKey = recreatePublicKey((ECPublicKey) keyPair.getPublic());
        PublicKey restoredPublicKey = deserialize(serialize(publicKey));

        assertThat(restoredPublicKey.getAlgorithm()).isEqualTo("EC");
        assertThat(restoredPublicKey.getEncoded()).isEqualTo(publicKey.getEncoded());
        assertThat(verifySignature(keyPair, restoredPublicKey)).isTrue();
    }

    private KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "EC", BouncyCastleFipsProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private PublicKey recreatePublicKey(ECPublicKey publicKey) throws Exception {
        ECPublicKeySpec keySpec = new ECPublicKeySpec(publicKey.getW(), publicKey.getParams());
        KeyFactory keyFactory = KeyFactory.getInstance("EC", BouncyCastleFipsProvider.PROVIDER_NAME);
        return keyFactory.generatePublic(keySpec);
    }

    private byte[] serialize(PublicKey publicKey) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(publicKey);
        }
        return bytes.toByteArray();
    }

    private PublicKey deserialize(byte[] serializedPublicKey) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(
            new ByteArrayInputStream(serializedPublicKey))) {
            return (PublicKey) input.readObject();
        }
    }

    private boolean verifySignature(KeyPair keyPair, PublicKey publicKey) throws Exception {
        byte[] message = "serialized EC public key".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance(
            "SHA256WITHECDSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        signer.initSign(keyPair.getPrivate());
        signer.update(message);

        Signature verifier = Signature.getInstance(
            "SHA256WITHECDSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        verifier.initVerify(publicKey);
        verifier.update(message);
        return verifier.verify(signer.sign());
    }
}
