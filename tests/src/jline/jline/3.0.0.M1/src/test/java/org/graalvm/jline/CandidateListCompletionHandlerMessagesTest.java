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
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.ExternalTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CandidateListCompletionHandlerMessagesTest {

    @Test
    @Timeout(10)
    void configuredCompleterReceivesParsedLineAndReturnsCandidateMetadata() throws Exception {
        TrackingCompleter completer = new TrackingCompleter();
        List<Candidate> candidates = new ArrayList<Candidate>();

        try (Terminal terminal = new ExternalTerminal(
                "completion-test",
                "ansi",
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8.name())) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .parser(new DefaultParser())
                    .completer(completer)
                    .build();
            ParsedLine parsedLine = reader.getParser().parse("deploy al", "deploy al".length());

            completer.complete(reader, parsedLine, candidates);
        }

        assertThat(completer.invocationCount).isEqualTo(1);
        assertThat(completer.lastWords).containsExactly("deploy", "al");
        assertThat(candidates).extracting(Candidate::value).containsExactly("alpha", "alpine");
        assertThat(candidates.get(0).displ()).isEqualTo("alpha command");
        assertThat(candidates.get(0).group()).isEqualTo("commands");
        assertThat(candidates.get(0).descr()).isEqualTo("first candidate");
        assertThat(candidates.get(0).suffix()).isEqualTo(" ");
        assertThat(candidates.get(0).key()).isEqualTo("a");
        assertThat(candidates.get(0).complete()).isTrue();
    }

    public static final class TrackingCompleter implements Completer {

        private int invocationCount;
        private List<String> lastWords;

        @Override
        public void complete(final LineReader reader, final ParsedLine line, final List<Candidate> candidates) {
            invocationCount++;
            lastWords = line.words();
            candidates.add(new Candidate("alpha", "alpha command", "commands", "first candidate", " ", "a", true));
            candidates.add(new Candidate("alpine"));
        }
    }
}
