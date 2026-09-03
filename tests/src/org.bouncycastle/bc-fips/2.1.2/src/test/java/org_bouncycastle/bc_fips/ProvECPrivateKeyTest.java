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
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPrivateKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvECPrivateKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesRestoresAndUsesAnEcPrivateKey() throws Exception {
        KeyPair keyPair = generateEcKeyPair();
        PrivateKey privateKey = recreatePrivateKey((ECPrivateKey) keyPair.getPrivate());
        byte[] serializedPrivateKey = serialize(privateKey);
        PrivateKey restoredPrivateKey = deserialize(serializedPrivateKey);
        PrivateKey roundTrippedPrivateKey = deserialize(serialize(restoredPrivateKey));

        assertThat(serializedPrivateKey).isNotEmpty();
        assertThat(roundTrippedPrivateKey.getAlgorithm()).isEqualTo("EC");
        assertThat(roundTrippedPrivateKey.getEncoded()).isEqualTo(privateKey.getEncoded());
        assertThat(signAndVerify(roundTrippedPrivateKey, keyPair.getPublic())).isTrue();
    }

    private KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "EC", BouncyCastleFipsProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private PrivateKey recreatePrivateKey(ECPrivateKey privateKey) throws Exception {
        ECPrivateKeySpec keySpec = new ECPrivateKeySpec(privateKey.getS(), privateKey.getParams());
        KeyFactory keyFactory = KeyFactory.getInstance("EC", BouncyCastleFipsProvider.PROVIDER_NAME);
        return keyFactory.generatePrivate(keySpec);
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

    private boolean signAndVerify(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        byte[] message = "serialized EC private key".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance(
            "SHA256WITHECDSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        signer.initSign(privateKey);
        signer.update(message);

        Signature verifier = Signature.getInstance(
            "SHA256WITHECDSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        verifier.initVerify(publicKey);
        verifier.update(message);
        return verifier.verify(signer.sign());
    }
}
