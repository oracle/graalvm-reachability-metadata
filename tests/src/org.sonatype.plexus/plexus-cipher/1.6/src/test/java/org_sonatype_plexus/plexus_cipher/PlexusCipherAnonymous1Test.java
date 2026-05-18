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
import org.sonatype.plexus.components.cipher.PlexusCipherException;

public class PlexusCipherAnonymous1Test {
    @Test
    void defaultCipherDecoratesAndUndecoratesThroughPlexusCipherInterface() throws PlexusCipherException {
        PlexusCipher cipher = new DefaultPlexusCipher();

        String value = "secret";
        String decorated = cipher.decorate(value);

        assertThat(decorated).startsWith(String.valueOf(PlexusCipher.ENCRYPTED_STRING_DECORATION_START));
        assertThat(decorated).endsWith(String.valueOf(PlexusCipher.ENCRYPTED_STRING_DECORATION_STOP));
        assertThat(cipher.isEncryptedString(decorated)).isTrue();
        assertThat(cipher.unDecorate(decorated)).isEqualTo(value);
        assertThat(cipher.isEncryptedString(value)).isFalse();
    }
}
