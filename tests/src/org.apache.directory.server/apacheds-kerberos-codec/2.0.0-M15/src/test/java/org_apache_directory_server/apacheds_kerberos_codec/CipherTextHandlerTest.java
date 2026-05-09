/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_directory_server.apacheds_kerberos_codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.directory.server.kerberos.shared.crypto.encryption.CipherTextHandler;
import org.apache.directory.server.kerberos.shared.crypto.encryption.KeyUsage;
import org.apache.directory.shared.kerberos.codec.types.EncryptionType;
import org.apache.directory.shared.kerberos.components.EncryptedData;
import org.apache.directory.shared.kerberos.components.EncryptionKey;
import org.junit.jupiter.api.Test;

public class CipherTextHandlerTest {
    @Test
    void encryptsAndDecryptsRc4HmacData() throws Exception {
        CipherTextHandler handler = new CipherTextHandler();
        byte[] keyBytes = "deterministic-key".getBytes(StandardCharsets.UTF_8);
        EncryptionKey key = new EncryptionKey(EncryptionType.RC4_HMAC, keyBytes, 3);
        byte[] plainText = "Apache Directory Kerberos cipher text".getBytes(StandardCharsets.UTF_8);

        EncryptedData encryptedData = handler.encrypt(key, plainText, KeyUsage.AP_REQ_AUTHNT_SESS_KEY);
        byte[] decryptedData = handler.decrypt(key, encryptedData, KeyUsage.AP_REQ_AUTHNT_SESS_KEY);

        assertThat(encryptedData.getEType()).isEqualTo(EncryptionType.RC4_HMAC);
        assertThat(encryptedData.getKvno()).isEqualTo(3);
        assertThat(encryptedData.getCipher()).containsExactly(plainText);
        assertThat(decryptedData).containsExactly(plainText);
    }
}
