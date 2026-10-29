/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_jtds.jtds;

import net.sourceforge.jtds.jdbc.CharsetInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CharsetInfoTest {
    @Test
    void resolvesServerCharsetsAndSqlServerCollations() throws Exception {
        CharsetInfo latin = CharsetInfo.getCharset("iso_1");
        assertThat(latin).isNotNull();
        assertThat(latin.getCharset()).isEqualTo("ISO-8859-1");
        assertThat(latin.isWideChars()).isFalse();

        CharsetInfo unicode = CharsetInfo.getCharset("utf8");
        assertThat(unicode).isNotNull();
        assertThat(unicode.getCharset()).isEqualTo("UTF-8");
        assertThat(unicode.isWideChars()).isTrue();

        CharsetInfo western = CharsetInfo.getCharsetForLCID(1033);
        assertThat(western).isNotNull();
        assertThat(western.getCharset()).isEqualTo("Cp1252");
        assertThat(CharsetInfo.getCharsetForSortOrder(52)).isEqualTo(western);
        assertThat(CharsetInfo.getCharset(new byte[]{9, 4, 0, 0, 0})).isEqualTo(western);
        assertThat(CharsetInfo.getCharset(new byte[]{0, 0, 0, 0, 52})).isEqualTo(western);
    }
}
