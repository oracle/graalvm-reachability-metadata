/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_directory_server.apacheds_kerberos_codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.directory.server.kerberos.shared.crypto.encryption.RandomKeyFactory;
import org.apache.directory.shared.kerberos.codec.types.EncryptionType;
import org.apache.directory.shared.kerberos.components.EncryptionKey;
import org.apache.directory.shared.kerberos.exceptions.KerberosException;
import org.junit.jupiter.api.Test;

public class ChecksumHandlerTest {
    @Test
    void createsAesSessionKey() throws Exception {
        EncryptionKey key = RandomKeyFactory.getRandomKey(EncryptionType.AES128_CTS_HMAC_SHA1_96);

        assertThat(key.getKeyType()).isEqualTo(EncryptionType.AES128_CTS_HMAC_SHA1_96);
        assertThat(key.getKeyValue()).hasSize(16);
    }

    @Test
    void rejectsUnsupportedSessionKeyType() {
        assertThatThrownBy(() -> RandomKeyFactory.getRandomKey(EncryptionType.UNKNOWN))
                .isInstanceOf(KerberosException.class);
    }
}
