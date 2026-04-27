/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.builtins.Completers;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class CandidateListCompletionHandlerMessagesTest {

    @Test
    void completerReturnsCommandOptionAndArgumentCandidates() {
        Completers.Completer completer = new Completers.Completer(new TestCompletionEnvironment());

        List<Candidate> commandCandidates = new ArrayList<Candidate>();
        completer.complete(null, new TestParsedLine("sh", List.of("sh"), 0, 2), commandCandidates);

        assertThat(commandCandidates)
                .extracting(Candidate::value)
                .containsOnly("show");

        List<Candidate> optionCandidates = new ArrayList<Candidate>();
        completer.complete(null, new TestParsedLine("show --f", List.of("show", "--f"), 1, 3), optionCandidates);

        assertThat(optionCandidates)
                .extracting(Candidate::value)
                .containsExactlyInAnyOrder("--file", "--format");
        assertThat(optionCandidates)
                .extracting(Candidate::descr)
                .containsExactlyInAnyOrder("Input file", "Output format");

        List<Candidate> argumentCandidates = new ArrayList<Candidate>();
        completer.complete(null, new TestParsedLine("show --file rep", List.of("show", "--file", "rep"), 2, 3), argumentCandidates);

        assertThat(argumentCandidates)
                .extracting(Candidate::value)
                .containsExactlyInAnyOrder("report.txt", "result.txt");
    }

    private static final class TestCompletionEnvironment implements Completers.CompletionEnvironment {

        private final Map<String, List<Completers.CompletionData>> completions = Map.of(
                "show",
                List.of(
                        new Completers.CompletionData(List.of("--file"), "Input file", "files", null),
                        new Completers.CompletionData(List.of("--format"), "Output format", null, null)));

        @Override
        public Map<String, List<Completers.CompletionData>> getCompletions() {
            return completions;
        }

        @Override
        public Set<String> getCommands() {
            return Set.of("show");
        }

        @Override
        public String resolveCommand(final String command) {
            return command;
        }

        @Override
        public String commandName(final String command) {
            return command;
        }

        @Override
        public Object evaluate(final LineReader reader, final ParsedLine line, final String function) {
            if ("files".equals(function)) {
                return List.of("report.txt", "result.txt");
            }
            return null;
        }
    }

    private static final class TestParsedLine implements ParsedLine {

        private final String line;
        private final List<String> words;
        private final int wordIndex;
        private final int wordCursor;

        private TestParsedLine(final String line, final List<String> words, final int wordIndex, final int wordCursor) {
            this.line = line;
            this.words = words;
            this.wordIndex = wordIndex;
            this.wordCursor = wordCursor;
        }

        @Override
        public String word() {
            return words.get(wordIndex);
        }

        @Override
        public int wordCursor() {
            return wordCursor;
        }

        @Override
        public int wordIndex() {
            return wordIndex;
        }

        @Override
        public List<String> words() {
            return words;
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
