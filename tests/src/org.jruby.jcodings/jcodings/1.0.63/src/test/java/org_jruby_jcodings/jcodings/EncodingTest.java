/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jruby_jcodings.jcodings;

import java.nio.charset.StandardCharsets;

import org.jcodings.Encoding;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EncodingTest {
    @Test
    void loadsUtf8EncodingByName() {
        Encoding encoding = Encoding.load("UTF8");

        assertThat(encoding.toString()).isEqualTo("UTF-8");
        assertThat(encoding.getCharsetName()).isEqualTo("UTF-8");
        assertThat(encoding.isUTF8()).isTrue();
        assertThat(encoding.isUnicode()).isTrue();
        assertThat(encoding.minLength()).isEqualTo(1);
        assertThat(encoding.maxLength()).isEqualTo(4);

        byte[] asciiBytes = "A\n".getBytes(StandardCharsets.UTF_8);
        assertThat(encoding.length(asciiBytes, 0, asciiBytes.length)).isEqualTo(1);
        assertThat(encoding.mbcToCode(asciiBytes, 0, asciiBytes.length)).isEqualTo('A');
        assertThat(encoding.isNewLine(asciiBytes, 1, asciiBytes.length)).isTrue();

        byte[] snowmanBytes = "\u2603".getBytes(StandardCharsets.UTF_8);
        assertThat(encoding.length(snowmanBytes, 0, snowmanBytes.length)).isEqualTo(3);
        assertThat(encoding.mbcToCode(snowmanBytes, 0, snowmanBytes.length)).isEqualTo(0x2603);
    }
}
