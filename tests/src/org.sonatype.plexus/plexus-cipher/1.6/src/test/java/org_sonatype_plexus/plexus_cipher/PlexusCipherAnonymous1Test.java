/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_plexus.plexus_cipher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.cipher.PlexusCipher;

public class PlexusCipherAnonymous1Test {
    @Test
    void encryptsDecoratesAndDecryptsText() throws Exception {
        PlexusCipher cipher = new DefaultPlexusCipher();
        String passPhrase = "native-image-passphrase";
        String plaintext = "metadata-test-value";

        String encrypted = cipher.encrypt(plaintext, passPhrase);
        String decorated = cipher.decorate(encrypted);

        assertThat(encrypted).isNotBlank().isNotEqualTo(plaintext);
        assertThat(decorated)
                .startsWith(String.valueOf(PlexusCipher.ENCRYPTED_STRING_DECORATION_START))
                .endsWith(String.valueOf(PlexusCipher.ENCRYPTED_STRING_DECORATION_STOP));
        assertThat(cipher.isEncryptedString(decorated)).isTrue();
        assertThat(cipher.unDecorate(decorated)).isEqualTo(encrypted);
        assertThat(cipher.decrypt(encrypted, passPhrase)).isEqualTo(plaintext);
        assertThat(cipher.decryptDecorated(decorated, passPhrase)).isEqualTo(plaintext);
    }
}
