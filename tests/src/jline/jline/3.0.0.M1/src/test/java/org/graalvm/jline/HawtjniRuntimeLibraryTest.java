/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.history.history.MemoryHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class HawtjniRuntimeLibraryTest {

    @Test
    void lineReaderBuilderUsesTheConfiguredHistoryImplementation() throws Exception {
        MemoryHistory history = new MemoryHistory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (Terminal terminal = TerminalBuilder.builder()
                .name("history-terminal")
                .type("ansi")
                .streams(new ByteArrayInputStream("first line\nsecond line\n".getBytes(StandardCharsets.UTF_8)), output)
                .build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("metadata-history")
                    .history(history)
                    .build();

            assertThat(reader.readLine("history> ")).isEqualTo("first line");
            assertThat(reader.readLine("history> ")).isEqualTo("second line");

            assertThat(reader.getHistory()).isSameAs(history);
            assertThat(history).extracting(History.Entry::value).containsExactly("first line", "second line");
        }

        assertThat(output.toString(StandardCharsets.UTF_8.name())).contains("history> ");
    }
}
