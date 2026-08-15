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
import java.security.interfaces.DSAPrivateKey;
import java.security.spec.DSAPrivateKeySpec;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvDSAPrivateKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesRestoresAndUsesADsaPrivateKey() throws Exception {
        KeyPair keyPair = generateDsaKeyPair();
        PrivateKey privateKey = recreatePrivateKey((DSAPrivateKey) keyPair.getPrivate());
        byte[] serializedPrivateKey = serialize(privateKey);
        PrivateKey restoredPrivateKey = deserialize(serializedPrivateKey);

        assertThat(restoredPrivateKey.getAlgorithm()).isEqualTo("DSA");
        assertThat(restoredPrivateKey.getEncoded()).isEqualTo(privateKey.getEncoded());
        assertThat(signAndVerify(restoredPrivateKey, keyPair.getPublic())).isTrue();
    }

    private KeyPair generateDsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "DSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private PrivateKey recreatePrivateKey(DSAPrivateKey privateKey) throws Exception {
        DSAPrivateKeySpec keySpec = new DSAPrivateKeySpec(
            privateKey.getX(), privateKey.getParams().getP(), privateKey.getParams().getQ(),
            privateKey.getParams().getG());
        KeyFactory keyFactory = KeyFactory.getInstance("DSA", BouncyCastleFipsProvider.PROVIDER_NAME);
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
        byte[] message = "serialized DSA private key".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256WITHDSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        signer.initSign(privateKey);
        signer.update(message);

        Signature verifier = Signature.getInstance("SHA256WITHDSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        verifier.initVerify(publicKey);
        verifier.update(message);
        return verifier.verify(signer.sign());
    }
}
