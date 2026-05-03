/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

import javax.crypto.SealedObject;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.flywaydb.core.internal.license.EncryptionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EncryptionUtilsTest {

    @Test
    void serializesAndDeserializesEncryptedPayload() throws Exception {
        final String algorithm = "AES/CBC/PKCS5Padding";
        final SecretKey key = EncryptionUtils.getKeyFromPassword("secret", "salt");
        final IvParameterSpec iv = new IvParameterSpec("0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        final String payload = "signed-license-payload";

        final SealedObject sealedObject = EncryptionUtils.encryptObject(algorithm, payload, key, iv);
        final byte[] serialized = EncryptionUtils.toByteArray(sealedObject);
        final SealedObject deserialized = EncryptionUtils.fromByteArray(serialized);
        final Serializable decrypted = EncryptionUtils.decryptObject(algorithm, deserialized, key, iv);

        assertThat(serialized).isNotEmpty();
        assertThat(decrypted).isEqualTo(payload);
    }
}
