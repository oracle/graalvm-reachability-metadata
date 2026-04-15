/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_codec.commons_codec;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.codec.language.DaitchMokotoffSoundex;
import org.junit.jupiter.api.Test;

class DaitchMokotoffSoundexTest {
    @Test
    void loadsBundledRulesWhenEncodingDocumentedExample() {
        DaitchMokotoffSoundex soundex = new DaitchMokotoffSoundex();

        assertThat(soundex.soundex("AUERBACH")).isEqualTo("097400|097500");
        assertThat(soundex.encode("AUERBACH")).isEqualTo("097400");
    }
}
