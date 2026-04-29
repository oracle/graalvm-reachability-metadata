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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CandidateListCompletionHandlerMessagesTest {

    @Test
    void completerProducesMultipleCandidatesForTheParsedCurrentWord() throws Exception {
        MultipleCandidateCompleter completer = new MultipleCandidateCompleter();
        List<Candidate> candidates = new ArrayList<Candidate>();

        try (Terminal terminal = TerminalBuilder.builder()
                .name("candidate-list-terminal")
                .type("ansi")
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("metadata-candidate-list")
                    .completer(completer)
                    .variable(LineReader.DISABLE_HISTORY, Boolean.TRUE)
                    .build();
            ParsedLine line = reader.getParser().parse("a", 1);

            completer.complete(reader, line, candidates);

            assertThat(line.word()).isEqualTo("a");
            assertThat(candidates).extracting(Candidate::value).containsExactly("alpha", "alphabet", "alpine");
        }
    }

    public static final class MultipleCandidateCompleter implements Completer {

        @Override
        public void complete(final LineReader reader, final ParsedLine line, final List<Candidate> candidates) {
            candidates.add(new Candidate("alpha"));
            candidates.add(new Candidate("alphabet"));
            candidates.add(new Candidate("alpine"));
        }
    }
}
