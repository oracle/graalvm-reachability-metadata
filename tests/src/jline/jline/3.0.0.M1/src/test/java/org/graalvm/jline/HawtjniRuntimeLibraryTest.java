/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.reader.ParsedLine;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.history.MemoryHistory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;


import static org.assertj.core.api.Assertions.assertThat;

public class HawtjniRuntimeLibraryTest {

    @Test
    @Timeout(10)
    void parserHandlesQuotingEscapingAndCursorMetadata() {
        DefaultParser parser = new DefaultParser();
        String commandLine = "deploy \"two words\" plain\\ value";

        ParsedLine parsedLine = parser.parse(commandLine, commandLine.length());

        assertThat(parsedLine.line()).isEqualTo(commandLine);
        assertThat(parsedLine.cursor()).isEqualTo(commandLine.length());
        assertThat(parsedLine.words()).containsExactly("deploy", "two words", "plain value");
        assertThat(parsedLine.wordIndex()).isEqualTo(2);
        assertThat(parsedLine.word()).isEqualTo("plain value");
        assertThat(parsedLine.wordCursor()).isEqualTo("plain value".length());
        assertThat(parser.getQuoteChars()).contains('"');
        assertThat(parser.getEscapeChars()).contains('\\');
    }

    @Test
    @Timeout(10)
    void memoryHistoryNavigatesUpdatesAndRemovesEntries() {
        MemoryHistory history = new MemoryHistory();
        history.setAutoTrim(true);
        history.setIgnoreDuplicates(true);

        history.add(" first ");
        history.add("second");
        history.add("second");
        history.set(history.last(), "third");

        assertThat(history).hasSize(2);
        assertThat(history.first()).isZero();
        assertThat(history.last()).isEqualTo(1);
        assertThat(history.get(0)).isEqualTo("first");
        assertThat(history.get(1)).isEqualTo("third");
        assertThat(history.moveToFirst()).isTrue();
        assertThat(history.current()).isEqualTo("first");
        assertThat(history.next()).isTrue();
        assertThat(history.current()).isEqualTo("third");
        history.replace("fourth");
        assertThat(history.get(1)).isEqualTo("fourth");
        assertThat(history.removeFirst()).isEqualTo("first");
        assertThat(history.removeLast()).isEqualTo("fourth");
        assertThat(history).isEmpty();
    }

}
