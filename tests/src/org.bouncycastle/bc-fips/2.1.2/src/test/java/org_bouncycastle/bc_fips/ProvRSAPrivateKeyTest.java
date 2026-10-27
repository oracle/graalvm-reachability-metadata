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
import java.security.Security;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.RSAPrivateKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvRSAPrivateKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesRestoresAndUsesANonCrtRsaPrivateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "RSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAPrivateKey generatedPrivateKey = (RSAPrivateKey) keyPair.getPrivate();
        KeyFactory keyFactory = KeyFactory.getInstance(
            "RSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        PrivateKey privateKey = keyFactory.generatePrivate(new RSAPrivateKeySpec(
            generatedPrivateKey.getModulus(), generatedPrivateKey.getPrivateExponent()));

        PrivateKey restoredPrivateKey = deserialize(serialize(privateKey));

        assertThat(restoredPrivateKey.getEncoded()).isEqualTo(privateKey.getEncoded());
        assertThat(signAndVerify(restoredPrivateKey, keyPair)).isTrue();
    }

    private byte[] serialize(PrivateKey privateKey) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(privateKey);
        }
        return bytes.toByteArray();
    }

    private PrivateKey deserialize(byte[] serializedKey) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(
            new ByteArrayInputStream(serializedKey))) {
            return (PrivateKey) input.readObject();
        }
    }

    private boolean signAndVerify(PrivateKey privateKey, KeyPair keyPair) throws Exception {
        byte[] message = "serialized non-CRT RSA key".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance(
            "SHA256WITHRSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        signer.initSign(privateKey);
        signer.update(message);

        Signature verifier = Signature.getInstance(
            "SHA256WITHRSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        verifier.initVerify(keyPair.getPublic());
        verifier.update(message);
        return verifier.verify(signer.sign());
    }
}
