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
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPrivateCrtKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvRSAPrivateCrtKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesRestoresAndUsesAnRsaPrivateCrtKey() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        PrivateKey privateKey = recreatePrivateKey((RSAPrivateCrtKey) keyPair.getPrivate());
        PrivateKey restoredPrivateKey = deserialize(serialize(privateKey));

        assertThat(restoredPrivateKey.getAlgorithm()).isEqualTo("RSA");
        assertThat(restoredPrivateKey.getEncoded()).isEqualTo(privateKey.getEncoded());
        assertThat(signAndVerify(restoredPrivateKey, keyPair.getPublic())).isTrue();
    }

    private KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "RSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private PrivateKey recreatePrivateKey(RSAPrivateCrtKey privateKey) throws Exception {
        RSAPrivateCrtKeySpec keySpec = new RSAPrivateCrtKeySpec(
            privateKey.getModulus(), privateKey.getPublicExponent(), privateKey.getPrivateExponent(),
            privateKey.getPrimeP(), privateKey.getPrimeQ(), privateKey.getPrimeExponentP(),
            privateKey.getPrimeExponentQ(), privateKey.getCrtCoefficient());
        KeyFactory keyFactory = KeyFactory.getInstance("RSA", BouncyCastleFipsProvider.PROVIDER_NAME);
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
        byte[] message = "serialized RSA private CRT key".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance(
            "SHA256WITHRSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        signer.initSign(privateKey);
        signer.update(message);

        Signature verifier = Signature.getInstance(
            "SHA256WITHRSA", BouncyCastleFipsProvider.PROVIDER_NAME);
        verifier.initVerify(publicKey);
        verifier.update(message);
        return verifier.verify(signer.sign());
    }
}
