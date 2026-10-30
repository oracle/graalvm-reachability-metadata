/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.sql.CharacterSet;
import org.junit.jupiter.api.Test;

public class CharacterSetTest {
    @Test
    void namesAndConvertsUnicodeCharacterSets() throws Exception {
        CharacterSet utf8 = CharacterSet.make(CharacterSet.AL32UTF8_CHARSET);
        CharacterSet utf16 = CharacterSet.make(CharacterSet.AL16UTF16_CHARSET);
        String text = "Oracle \u20ac \u03bb";

        assertThat(utf8.toString()).isEqualTo("AL32UTF8");
        assertThat(utf16.toString()).isEqualTo("AL16UTF16");
        byte[] encoded = utf8.convert(text);
        assertThat(utf8.toString(encoded, 0, encoded.length)).isEqualTo(text);
        byte[] converted = utf16.convert(utf8, encoded, 0, encoded.length);
        assertThat(utf16.toString(converted, 0, converted.length)).isEqualTo(text);
    }
}
