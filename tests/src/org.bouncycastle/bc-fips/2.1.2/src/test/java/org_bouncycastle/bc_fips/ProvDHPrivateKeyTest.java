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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;

import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.spec.DHParameterSpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvDHPrivateKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesAndRestoresADiffieHellmanPrivateKey() throws Exception {
        DHParameterSpec parameters = parametersFromJdkProvider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "DH", BouncyCastleFipsProvider.PROVIDER_NAME);
        generator.initialize(parameters);
        KeyPair keyPair = generator.generateKeyPair();
        PrivateKey restoredPrivateKey = deserialize(serialize(keyPair.getPrivate()));

        assertThat(restoredPrivateKey.getAlgorithm()).isEqualTo("DH");
        assertThat(restoredPrivateKey.getEncoded()).isEqualTo(keyPair.getPrivate().getEncoded());
    }

    private DHParameterSpec parametersFromJdkProvider() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("DH", "SunJCE");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return ((DHPrivateKey) keyPair.getPrivate()).getParams();
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
}
