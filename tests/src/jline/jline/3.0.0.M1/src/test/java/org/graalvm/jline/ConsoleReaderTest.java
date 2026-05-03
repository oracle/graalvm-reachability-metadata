/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsoleReaderTest {

    @Test
    void lineReaderReadsInputAndWritesPromptThroughTheConfiguredTerminal() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DumbTerminal terminal = new DumbTerminal(
                "console-reader-test",
                "ansi",
                new ByteArrayInputStream("hello jline\n".getBytes(StandardCharsets.UTF_8)),
                output,
                StandardCharsets.UTF_8.name());

        try {
            LineReader reader = LineReaderBuilder.builder()
                    .appName("console-reader-test")
                    .terminal(terminal)
                    .build();

            String line = reader.readLine("prompt> ");
            terminal.flush();

            assertThat(line).isEqualTo("hello jline");
            assertThat(reader.getTerminal()).isSameAs(terminal);
            assertThat(output.toString(StandardCharsets.UTF_8.name())).contains("prompt> ");
        } finally {
            terminal.reader().close();
            terminal.close();
        }
    }
}
