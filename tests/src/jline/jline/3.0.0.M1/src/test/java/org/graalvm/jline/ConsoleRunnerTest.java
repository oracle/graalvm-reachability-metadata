/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.reader.Expander;
import org.jline.reader.Highlighter;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.history.MemoryHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.ExternalTerminal;
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsoleRunnerTest {

    @Test
    @Timeout(10)
    void builderInstallsCustomReaderComponents() throws Exception {
        History history = new MemoryHistory();
        Highlighter highlighter = (reader, buffer) -> new AttributedString("highlighted:" + buffer);
        Expander expander = new PrefixExpander();

        try (Terminal terminal = new ExternalTerminal(
                "builder-components-test",
                "ansi",
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8.name())) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("component-test")
                    .parser(new DefaultParser())
                    .highlighter(highlighter)
                    .history(history)
                    .expander(expander)
                    .variable(LineReader.BELL_STYLE, "none")
                    .build();

            history.add("build native");

            assertThat(reader.getTerminal()).isSameAs(terminal);
            assertThat(reader.getParser().parse("run \"native image\"", "run \"native image\"".length()).words())
                    .containsExactly("run", "native image", "");
            assertThat(reader.getHighlighter().highlight(reader, "buffer").toString()).isEqualTo("highlighted:buffer");
            assertThat(reader.getExpander().expandHistory(history, "again")).isEqualTo("expanded:again:build native");
            assertThat(reader.getVariable(LineReader.BELL_STYLE)).isEqualTo("none");
            assertThat(reader.getHistory()).isSameAs(history);
        }
    }

    public static final class PrefixExpander implements Expander {

        @Override
        public String expandHistory(final History history, final String line) {
            return "expanded:" + line + ":" + history.get(history.last());
        }

        @Override
        public String expandVar(final String word) {
            return "var:" + word;
        }
    }
}
