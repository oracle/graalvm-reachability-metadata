/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.reader.ParsedLine;
import org.jline.reader.impl.DefaultParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HawtjniRuntimeLibraryTest {

    @Test
    void defaultParserKeepsQuotedWordsAndEscapedWhitespaceTogether() throws Exception {
        DefaultParser parser = new DefaultParser();
        String line = "command \"quoted value\" escaped\\ word";

        ParsedLine parsedLine = parser.parse(line, line.length());

        assertThat(parsedLine.words()).containsExactly("command", "quoted value", "escaped word");
        assertThat(parsedLine.wordIndex()).isEqualTo(2);
        assertThat(parsedLine.word()).isEqualTo("escaped word");
        assertThat(parsedLine.wordCursor()).isEqualTo("escaped word".length());
    }
}
