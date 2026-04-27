/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.history.history.FileHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.ExternalTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsoleRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void fileHistoryPersistsAcceptedLines() throws Exception {
        Path historyFile = temporaryDirectory.resolve("jline-history.txt");

        try (PipedInputStream terminalInput = new PipedInputStream();
             PipedOutputStream inputWriter = new PipedOutputStream(terminalInput);
             ByteArrayOutputStream terminalOutput = new ByteArrayOutputStream();
             Terminal terminal = new ExternalTerminal(
                     "console-runner-test",
                     "ansi",
                     terminalInput,
                     terminalOutput,
                     StandardCharsets.UTF_8.name())) {
            FileHistory history = new FileHistory(historyFile.toFile());
            LineReader reader = LineReaderBuilder.builder()
                    .appName("console-runner-test")
                    .history(history)
                    .terminal(terminal)
                    .build();

            inputWriter.write("alpha\nsecond value\n".getBytes(StandardCharsets.UTF_8));
            inputWriter.flush();

            assertThat(reader.readLine("first> ")).isEqualTo("alpha");
            assertThat(reader.readLine("second> ")).isEqualTo("second value");

            history.flush();

            assertThat(historyFile).exists();
            assertThat(Files.readAllLines(historyFile, StandardCharsets.UTF_8))
                    .containsExactly("alpha", "second value");
        }
    }
}
