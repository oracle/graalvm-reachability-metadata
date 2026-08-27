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

import javax.crypto.KeyAgreement;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvXDHPrivateKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesRestoresAndUsesAnX25519PrivateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "X25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        KeyPair localKeyPair = generator.generateKeyPair();
        KeyPair peerKeyPair = generator.generateKeyPair();
        PrivateKey restoredPrivateKey = deserialize(serialize(localKeyPair.getPrivate()));

        byte[] restoredSecret = agree(restoredPrivateKey, peerKeyPair);
        byte[] peerSecret = agree(peerKeyPair.getPrivate(), localKeyPair);

        assertThat(restoredPrivateKey.getEncoded())
            .isEqualTo(localKeyPair.getPrivate().getEncoded());
        assertThat(restoredSecret).isEqualTo(peerSecret);
    }

    private byte[] agree(PrivateKey privateKey, KeyPair peerKeyPair) throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance(
            "X25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        agreement.init(privateKey);
        agreement.doPhase(peerKeyPair.getPublic(), true);
        return agreement.generateSecret();
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
}
