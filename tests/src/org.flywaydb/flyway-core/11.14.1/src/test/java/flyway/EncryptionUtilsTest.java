/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.io.Serializable;

import javax.crypto.SealedObject;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.flywaydb.core.internal.license.EncryptionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EncryptionUtilsTest {
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final IvParameterSpec IV = new IvParameterSpec(new byte[] {
        10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25
    });

    @Test
    void serializesAndDeserializesSealedObject() throws Exception {
        SecretKey key = EncryptionUtils.getKeyFromPassword("flyway-password", "flyway-salt");
        SealedObject sealedObject = EncryptionUtils.encryptObject(ALGORITHM, "licensed", key, IV);

        byte[] serialized = EncryptionUtils.toByteArray(sealedObject);
        SealedObject deserialized = EncryptionUtils.fromByteArray(serialized);
        Serializable decrypted = EncryptionUtils.decryptObject(ALGORITHM, deserialized, key, IV);

        assertThat(serialized).isNotEmpty();
        assertThat(decrypted).isEqualTo("licensed");
    }
}
