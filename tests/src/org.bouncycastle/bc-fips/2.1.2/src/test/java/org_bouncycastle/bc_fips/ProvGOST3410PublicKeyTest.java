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

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.jcajce.spec.GOST3410PublicKeySpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvGOST3410PublicKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesRestoresAndUsesAGost3410PublicKey() throws Exception {
        KeyPair keyPair = generateGost3410KeyPair();
        PublicKey publicKey = recreatePublicKey(keyPair.getPublic());
        PublicKey restoredPublicKey = deserialize(serialize(publicKey));
        PublicKey reserializedPublicKey = deserialize(serialize(restoredPublicKey));

        assertThat(restoredPublicKey.getAlgorithm()).isEqualTo("GOST3410");
        assertThat(restoredPublicKey.getEncoded()).isEqualTo(publicKey.getEncoded());
        assertThat(reserializedPublicKey.getEncoded()).isEqualTo(publicKey.getEncoded());
        assertThat(verifiesSignature(keyPair, restoredPublicKey)).isTrue();
        assertThat(verifiesSignature(keyPair, reserializedPublicKey)).isTrue();
    }

    private KeyPair generateGost3410KeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "GOST3410", BouncyCastleFipsProvider.PROVIDER_NAME);
        return generator.generateKeyPair();
    }

    private PublicKey recreatePublicKey(PublicKey publicKey) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(
            "GOST3410", BouncyCastleFipsProvider.PROVIDER_NAME);
        GOST3410PublicKeySpec keySpec = keyFactory.getKeySpec(publicKey, GOST3410PublicKeySpec.class);
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

    private boolean verifiesSignature(KeyPair keyPair, PublicKey publicKey) throws Exception {
        byte[] message = "serialized GOST3410 public key".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("GOST3410", BouncyCastleFipsProvider.PROVIDER_NAME);
        signer.initSign(keyPair.getPrivate());
        signer.update(message);

        Signature verifier = Signature.getInstance("GOST3410", BouncyCastleFipsProvider.PROVIDER_NAME);
        verifier.initVerify(publicKey);
        verifier.update(message);
        return verifier.verify(signer.sign());
    }
}
