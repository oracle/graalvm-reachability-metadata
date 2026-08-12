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
import java.security.spec.ECGenParameterSpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvECGOST3410PublicKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesRestoresAndUsesAnEcGost3410PublicKey() throws Exception {
        KeyPair keyPair = generateEcGost3410KeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PublicKey restoredPublicKey = deserialize(serialize(publicKey));

        assertThat(restoredPublicKey.getAlgorithm()).isEqualTo("ECGOST3410");
        assertThat(restoredPublicKey.getEncoded()).isEqualTo(publicKey.getEncoded());
        assertThat(verifiesSignature(keyPair, restoredPublicKey)).isTrue();
    }

    private KeyPair generateEcGost3410KeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "ECGOST3410", BouncyCastleFipsProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("GostR3410-2001-CryptoPro-A"));
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

    private boolean verifiesSignature(KeyPair keyPair, PublicKey publicKey) throws Exception {
        byte[] message = "serialized ECGOST3410 public key".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance(
            "GOST3411WITHECGOST3410", BouncyCastleFipsProvider.PROVIDER_NAME);
        signer.initSign(keyPair.getPrivate());
        signer.update(message);

        Signature verifier = Signature.getInstance(
            "GOST3411WITHECGOST3410", BouncyCastleFipsProvider.PROVIDER_NAME);
        verifier.initVerify(publicKey);
        verifier.update(message);
        return verifier.verify(signer.sign());
    }
}
