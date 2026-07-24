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

import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvDHPublicKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesAndRestoresADiffieHellmanPublicKey() throws Exception {
        DHParameterSpec parameters = parametersFromJdkProvider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "DH", BouncyCastleFipsProvider.PROVIDER_NAME);
        generator.initialize(parameters);
        KeyPair keyPair = generator.generateKeyPair();
        DHPublicKey generatedPublicKey = (DHPublicKey) keyPair.getPublic();
        KeyFactory keyFactory = KeyFactory.getInstance("DH", BouncyCastleFipsProvider.PROVIDER_NAME);
        DHPublicKey publicKey = (DHPublicKey) keyFactory.generatePublic(new DHPublicKeySpec(
            generatedPublicKey.getY(), parameters.getP(), parameters.getG()));
        DHPublicKey restoredPublicKey = deserialize(serialize(publicKey));
        DHPublicKey restoredUnsharedPublicKey = deserializeUnshared(serializeUnshared(publicKey));

        assertThat(restoredPublicKey.getAlgorithm()).isEqualTo("DH");
        assertThat(restoredPublicKey.getY()).isEqualTo(generatedPublicKey.getY());
        assertThat(restoredPublicKey.getEncoded()).isEqualTo(publicKey.getEncoded());
        assertThat(restoredUnsharedPublicKey.getEncoded()).isEqualTo(publicKey.getEncoded());
    }

    private DHParameterSpec parametersFromJdkProvider() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("DH", "SunJCE");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return ((DHPrivateKey) keyPair.getPrivate()).getParams();
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

    private DHPublicKey deserialize(byte[] serializedPublicKey) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(
            new ByteArrayInputStream(serializedPublicKey))) {
            return (DHPublicKey) input.readObject();
        }
    }

    private DHPublicKey deserializeUnshared(byte[] serializedPublicKey) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(
            new ByteArrayInputStream(serializedPublicKey))) {
            return (DHPublicKey) input.readUnshared();
        }
    }
}
