/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_directory_server.apacheds_kerberos_codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.directory.shared.kerberos.codec.KerberosDecoder;
import org.apache.directory.shared.kerberos.codec.types.EncryptionType;
import org.apache.directory.shared.kerberos.components.EncryptionKey;
import org.apache.directory.shared.kerberos.exceptions.KerberosException;
import org.junit.jupiter.api.Test;

public class ChecksumHandlerTest {
    @Test
    void encodesAndDecodesEncryptionKey() throws Exception {
        byte[] keyValue = "Apache Directory Kerberos key".getBytes(StandardCharsets.UTF_8);
        EncryptionKey originalKey = new EncryptionKey(EncryptionType.AES128_CTS_HMAC_SHA1_96, keyValue, 5);
        ByteBuffer encodedKey = ByteBuffer.allocate(originalKey.computeLength());

        originalKey.encode(encodedKey);
        EncryptionKey decodedKey = KerberosDecoder.decodeEncryptionKey(encodedKey.array());

        assertThat(decodedKey.getKeyType()).isEqualTo(EncryptionType.AES128_CTS_HMAC_SHA1_96);
        assertThat(decodedKey.getKeyValue()).containsExactly(keyValue);
        assertThat(decodedKey).isEqualTo(new EncryptionKey(EncryptionType.AES128_CTS_HMAC_SHA1_96, keyValue));
    }

    @Test
    void rejectsInvalidEncryptionKeyEncoding() throws Exception {
        byte[] keyValue = "Apache Directory Kerberos key".getBytes(StandardCharsets.UTF_8);
        EncryptionKey originalKey = new EncryptionKey(EncryptionType.AES128_CTS_HMAC_SHA1_96, keyValue);
        ByteBuffer encodedKey = ByteBuffer.allocate(originalKey.computeLength());
        originalKey.encode(encodedKey);
        byte[] invalidEncoding = encodedKey.array().clone();
        invalidEncoding[0] = 0x31;

        assertThatThrownBy(() -> KerberosDecoder.decodeEncryptionKey(invalidEncoding))
                .isInstanceOf(KerberosException.class);
    }
}
