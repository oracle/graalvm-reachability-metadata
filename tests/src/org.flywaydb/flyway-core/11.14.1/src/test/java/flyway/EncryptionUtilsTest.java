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
    void serializesEncryptedLicensePayloadForStorageAndReload() throws Exception {
        String algorithm = "AES/CBC/PKCS5Padding";
        SecretKey key = EncryptionUtils.getKeyFromPassword("password", "salt");
        IvParameterSpec initializationVector = new IvParameterSpec(
                "0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
        String payload = "flyway-license-payload";

        SealedObject sealedPayload = EncryptionUtils.encryptObject(algorithm, payload, key, initializationVector);
        byte[] serializedPayload = EncryptionUtils.toByteArray(sealedPayload);
        SealedObject reloadedPayload = EncryptionUtils.fromByteArray(serializedPayload);
        Serializable decryptedPayload = EncryptionUtils.decryptObject(
                algorithm, reloadedPayload, key, initializationVector);

        assertThat(serializedPayload).isNotEmpty();
        assertThat(decryptedPayload).isEqualTo(payload);
    }
}
