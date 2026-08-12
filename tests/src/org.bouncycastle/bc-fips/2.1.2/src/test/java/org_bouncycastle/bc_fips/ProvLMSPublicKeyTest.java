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
import org.bouncycastle.jcajce.spec.LMOtsParameters;
import org.bouncycastle.jcajce.spec.LMSKeyGenParameterSpec;
import org.bouncycastle.jcajce.spec.LMSigParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvLMSPublicKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesRestoresAndUsesAnLmsPublicKey() throws Exception {
        KeyPair keyPair = generateLmsKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PublicKey restoredPublicKey = deserialize(serialize(publicKey));

        assertThat(restoredPublicKey.getAlgorithm()).isEqualTo("LMS");
        assertThat(restoredPublicKey.getEncoded()).isEqualTo(publicKey.getEncoded());
        assertThat(verifySignature(keyPair, restoredPublicKey)).isTrue();
    }

    private KeyPair generateLmsKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "LMS", BouncyCastleFipsProvider.PROVIDER_NAME);
        generator.initialize(new LMSKeyGenParameterSpec(
            LMSigParameters.lms_sha256_n32_h5, LMOtsParameters.sha256_n32_w4));
        return generator.generateKeyPair();
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
        byte[] message = "serialized LMS public key".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("LMS", BouncyCastleFipsProvider.PROVIDER_NAME);
        signer.initSign(keyPair.getPrivate());
        signer.update(message);

        Signature verifier = Signature.getInstance("LMS", BouncyCastleFipsProvider.PROVIDER_NAME);
        verifier.initVerify(publicKey);
        verifier.update(message);
        return verifier.verify(signer.sign());
    }
}
