/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import javax.crypto.SealedObject;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.flywaydb.core.internal.license.EncryptionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EncryptionUtilsTest {
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final IvParameterSpec INITIALIZATION_VECTOR = new IvParameterSpec(new byte[] {
            1, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53
    });

    @Test
    void sealedObjectRoundTripsThroughSerializedBytes() throws Exception {
        SecretKey secretKey = EncryptionUtils.getKeyFromPassword("flyway-password", "flyway-salt");
        SealedObject sealedObject = EncryptionUtils.encryptObject(ALGORITHM,
                "licensed payload",
                secretKey,
                INITIALIZATION_VECTOR);

        byte[] serialized = EncryptionUtils.toByteArray(sealedObject);
        SealedObject deserialized = EncryptionUtils.fromByteArray(serialized);

        assertThat(serialized).isNotEmpty();
        assertThat(EncryptionUtils.decryptObject(ALGORITHM, deserialized, secretKey, INITIALIZATION_VECTOR))
                .isEqualTo("licensed payload");
    }
}
