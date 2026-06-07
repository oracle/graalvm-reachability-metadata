/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_directory_server.apacheds_kerberos_codec;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.directory.server.kerberos.shared.crypto.encryption.RandomKeyFactory;
import org.apache.directory.shared.kerberos.codec.types.EncryptionType;
import org.apache.directory.shared.kerberos.components.EncryptionKey;
import org.junit.jupiter.api.Test;

public class CipherTextHandlerTest {
    @Test
    void createsAndDestroysAesSessionKey() throws Exception {
        EncryptionKey sessionKey = RandomKeyFactory.getRandomKey(EncryptionType.AES128_CTS_HMAC_SHA1_96);
        byte[] keyValue = sessionKey.getKeyValue();

        assertThat(sessionKey.getKeyType()).isEqualTo(EncryptionType.AES128_CTS_HMAC_SHA1_96);
        assertThat(sessionKey.getKeyVersion()).isZero();
        assertThat(keyValue).hasSize(16);

        sessionKey.destroy();

        assertThat(keyValue).containsOnly((byte) 0x00);
        assertThat(sessionKey.getKeyValue()).containsOnly((byte) 0x00);
    }
}
