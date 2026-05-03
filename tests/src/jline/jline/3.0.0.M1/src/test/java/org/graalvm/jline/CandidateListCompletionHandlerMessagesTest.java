/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.completer.StringsCompleter;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CandidateListCompletionHandlerMessagesTest {

    @Test
    void stringsCompleterAddsDisplayCandidatesForParsedInput() throws Exception {
        DumbTerminal terminal = new DumbTerminal(
                "candidate-completion-test",
                "ansi",
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8.name());

        try {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new StringsCompleter("alpha", "beta"))
                    .build();
            ParsedLine parsedLine = new DefaultParser().parse("a", 1);
            List<Candidate> candidates = new ArrayList<Candidate>();

            new StringsCompleter("alpha", "beta").complete(reader, parsedLine, candidates);

            assertThat(parsedLine.word()).isEqualTo("a");
            assertThat(candidates).extracting(Candidate::value).containsExactly("alpha", "beta");
            assertThat(candidates).allMatch(Candidate::complete);
        } finally {
            terminal.reader().close();
            terminal.close();
        }
    }
}
