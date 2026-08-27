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
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvSecretKeySpecTest {
    private static final byte[] KEY_BYTES = new byte[] {
        1, 2, 4, 8, 16, 32, 64, 127
    };

    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void serializesRestoresAndUsesAProviderSecretKey() throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(
            "DES", BouncyCastleFipsProvider.PROVIDER_NAME);
        SecretKey key = factory.generateSecret(new SecretKeySpec(KEY_BYTES, "DES"));

        SecretKey restoredKey = deserialize(serialize(key));
        byte[] plaintext = "provider secret key".getBytes(StandardCharsets.UTF_8);
        Cipher encryptor = Cipher.getInstance(
            "DES/ECB/PKCS5Padding", BouncyCastleFipsProvider.PROVIDER_NAME);
        encryptor.init(Cipher.ENCRYPT_MODE, restoredKey);
        byte[] ciphertext = encryptor.doFinal(plaintext);
        Cipher decryptor = Cipher.getInstance(
            "DES/ECB/PKCS5Padding", BouncyCastleFipsProvider.PROVIDER_NAME);
        decryptor.init(Cipher.DECRYPT_MODE, restoredKey);

        assertThat(restoredKey.getEncoded()).isEqualTo(key.getEncoded());
        assertThat(decryptor.doFinal(ciphertext)).isEqualTo(plaintext);
    }

    private byte[] serialize(SecretKey key) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(key);
        }
        return bytes.toByteArray();
    }

    private SecretKey deserialize(byte[] serializedKey) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(
            new ByteArrayInputStream(serializedKey))) {
            return (SecretKey) input.readObject();
        }
    }
}
