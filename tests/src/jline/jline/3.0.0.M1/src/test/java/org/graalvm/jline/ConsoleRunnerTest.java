/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsoleRunnerTest {

    @Test
    void lineReaderInvokesConfiguredCompleterWhenTabCompletingInput() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TrackingCompleter.constructorCalls = 0;
        TrackingCompleter.completionCalls = 0;
        TrackingCompleter.lastWord = null;

        try (Terminal terminal = TerminalBuilder.builder()
                .name("completion-terminal")
                .type("ansi")
                .streams(new ByteArrayInputStream("al\t\n".getBytes(StandardCharsets.UTF_8)), output)
                .build()) {
            TrackingCompleter completer = new TrackingCompleter();
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("metadata-completion")
                    .completer(completer)
                    .variable(LineReader.DISABLE_HISTORY, Boolean.TRUE)
                    .build();

            String line = reader.readLine("complete> ");

            assertThat(line).isEqualTo("alpha ");
            assertThat(TrackingCompleter.constructorCalls).isEqualTo(1);
            assertThat(TrackingCompleter.completionCalls).isEqualTo(1);
            assertThat(TrackingCompleter.lastWord).isEqualTo("al");
        }

        assertThat(output.toString(StandardCharsets.UTF_8.name())).contains("complete> ");
    }

    public static final class TrackingCompleter implements Completer {

        private static int constructorCalls;
        private static int completionCalls;
        private static String lastWord;

        public TrackingCompleter() {
            constructorCalls++;
        }

        @Override
        public void complete(final LineReader reader, final ParsedLine line, final List<Candidate> candidates) {
            completionCalls++;
            lastWord = line.word();
            candidates.add(new Candidate("alpha"));
        }
    }
}
