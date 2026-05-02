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
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class HawtjniRuntimeLibraryTest {

    @Test
    @Timeout(10)
    void lineReaderRecordsAcceptedLinesInHistory() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (Terminal terminal = TerminalBuilder.builder()
                .name("history-reader-test")
                .type("ansi")
                .streams(input, output)
                .system(false)
                .build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("history-reader-test")
                    .build();

            assertThat(reader.readLine()).isEqualTo("first");
            assertThat(reader.readLine()).isEqualTo("second");

            History history = reader.getHistory();
            assertThat(history.size()).isEqualTo(2);
            assertThat(history.get(0)).isEqualTo("first");
            assertThat(history.get(1)).isEqualTo("second");
        }
    }
}
