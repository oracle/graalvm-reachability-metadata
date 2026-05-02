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
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsoleRunnerTest {

    @Test
    @Timeout(10)
    void lineReaderBuilderInstallsTheConfiguredCompleter() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TrackingCompleter completer = new TrackingCompleter();

        try (Terminal terminal = TerminalBuilder.builder()
                .name("completion-reader-test")
                .type("ansi")
                .streams(input, output)
                .system(false)
                .build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("completion-reader-test")
                    .completer(completer)
                    .variable(LineReader.DISABLE_HISTORY, Boolean.TRUE)
                    .build();
            List<Candidate> candidates = new ArrayList<Candidate>();

            completer.complete(reader, new TestParsedLine("can"), candidates);

            assertThat(reader.getTerminal()).isSameAs(terminal);
            assertThat(completer.completedLines).containsExactly("can");
            assertThat(candidates).singleElement().extracting(Candidate::value).isEqualTo("candidate");
        }
    }

    public static final class TrackingCompleter implements Completer {

        private final List<String> completedLines = new ArrayList<String>();

        @Override
        public void complete(final LineReader reader, final ParsedLine line, final List<Candidate> candidates) {
            completedLines.add(line.line());
            candidates.add(new Candidate("candidate"));
        }
    }

    private static final class TestParsedLine implements ParsedLine {

        private final String line;

        private TestParsedLine(final String line) {
            this.line = line;
        }

        @Override
        public String word() {
            return line;
        }

        @Override
        public int wordCursor() {
            return line.length();
        }

        @Override
        public int wordIndex() {
            return 0;
        }

        @Override
        public List<String> words() {
            return Collections.singletonList(line);
        }

        @Override
        public String line() {
            return line;
        }

        @Override
        public int cursor() {
            return line.length();
        }
    }
}
