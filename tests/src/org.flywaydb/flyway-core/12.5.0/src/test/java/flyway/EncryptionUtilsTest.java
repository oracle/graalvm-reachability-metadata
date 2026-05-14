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
import javax.crypto.spec.SecretKeySpec;

import org.flywaydb.core.internal.license.EncryptionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EncryptionUtilsTest {

    @Test
    void serializesEncryptedPayloadForOfflineLicenseHandling() throws Exception {
        final String algorithm = "AES/CBC/PKCS5Padding";
        final SecretKey key = new SecretKeySpec("0123456789abcdef".getBytes(StandardCharsets.UTF_8), "AES");
        final IvParameterSpec iv = new IvParameterSpec("abcdef9876543210".getBytes(StandardCharsets.UTF_8));
        final String payload = "flyway-offline-license-payload";

        final SealedObject encryptedPayload = EncryptionUtils.encryptObject(algorithm, payload, key, iv);
        final byte[] serializedPayload = EncryptionUtils.toByteArray(encryptedPayload);
        final SealedObject restoredPayload = EncryptionUtils.fromByteArray(serializedPayload);
        final Serializable decryptedPayload = EncryptionUtils.decryptObject(algorithm, restoredPayload, key, iv);

        assertThat(serializedPayload).isNotEmpty();
        assertThat(decryptedPayload).isEqualTo(payload);
    }
}
