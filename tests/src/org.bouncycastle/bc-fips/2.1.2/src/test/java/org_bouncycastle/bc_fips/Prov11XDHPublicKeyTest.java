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
import java.security.PublicKey;
import java.security.Security;

import javax.crypto.KeyAgreement;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Prov11XDHPublicKeyTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesAndRestoresAnX25519PublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
            "X25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        KeyPair keyPair = generator.generateKeyPair();
        KeyPair peerKeyPair = generator.generateKeyPair();

        PublicKey restoredPublicKey = deserialize(serialize(keyPair.getPublic()));

        assertThat(restoredPublicKey.getAlgorithm()).isEqualTo("X25519");
        assertThat(restoredPublicKey.getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
        assertThat(agree(peerKeyPair, keyPair.getPublic())).isEqualTo(agree(peerKeyPair, restoredPublicKey));
    }

    private byte[] serialize(PublicKey publicKey) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(publicKey);
        }
        return bytes.toByteArray();
    }

    private PublicKey deserialize(byte[] serializedPublicKey) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedPublicKey))) {
            return (PublicKey)input.readObject();
        }
    }

    private byte[] agree(KeyPair keyPair, PublicKey publicKey) throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance(
            "X25519", BouncyCastleFipsProvider.PROVIDER_NAME);
        agreement.init(keyPair.getPrivate());
        agreement.doPhase(publicKey, true);
        return agreement.generateSecret();
    }
}
