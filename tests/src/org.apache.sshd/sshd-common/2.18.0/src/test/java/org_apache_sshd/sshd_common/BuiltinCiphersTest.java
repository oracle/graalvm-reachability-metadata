/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.cipher.Cipher;
import org.apache.sshd.common.cipher.CipherFactory;
import org.junit.jupiter.api.Test;

public class BuiltinCiphersTest {
    @Test
    void encryptsAndDecryptsPayloadWithAesCtr() throws Exception {
        CipherFactory factory = BuiltinCiphers.aes128ctr;
        byte[] key = new byte[factory.getKeySize() / Byte.SIZE];
        byte[] iv = new byte[factory.getIVSize()];
        byte[] payload = "SSH cipher payload".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = payload.clone();

        assertThat(factory.isSupported()).isTrue();

        Cipher encryptor = factory.create();
        encryptor.init(Cipher.Mode.Encrypt, key, iv);
        encryptor.update(encrypted, 0, encrypted.length);
        assertThat(encrypted).isNotEqualTo(payload);

        Cipher decryptor = factory.create();
        decryptor.init(Cipher.Mode.Decrypt, key, iv);
        decryptor.update(encrypted, 0, encrypted.length);

        assertThat(encrypted).isEqualTo(payload);
    }
}
