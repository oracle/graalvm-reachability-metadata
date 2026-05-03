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
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsoleRunnerTest {

    @Test
    void lineReaderBuilderUsesTheConfiguredCompleter() throws Exception {
        TrackingCompleter completer = new TrackingCompleter();
        DumbTerminal terminal = new DumbTerminal(
                "line-reader-builder-test",
                "ansi",
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8.name());

        try {
            LineReader reader = LineReaderBuilder.builder()
                    .appName("line-reader-builder-test")
                    .terminal(terminal)
                    .completer(completer)
                    .build();
            ParsedLine parsedLine = new DefaultParser().parse("can", 3);
            List<Candidate> candidates = new ArrayList<Candidate>();

            completer.complete(reader, parsedLine, candidates);

            assertThat(completer.invocationCount).isEqualTo(1);
            assertThat(candidates).extracting(Candidate::value).containsExactly("candidate");
        } finally {
            terminal.reader().close();
            terminal.close();
        }
    }

    public static final class TrackingCompleter implements Completer {

        private int invocationCount;

        @Override
        public void complete(final LineReader reader, final ParsedLine line, final List<Candidate> candidates) {
            invocationCount++;
            candidates.add(new Candidate("candidate"));
        }
    }
}
