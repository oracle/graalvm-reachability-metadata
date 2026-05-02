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
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class CandidateListCompletionHandlerMessagesTest {

    @Test
    @Timeout(10)
    void builtinCompleterAddsCommandsAliasesOptionsAndArguments() {
        CompletionEnvironment environment = new CompletionEnvironment();
        Completers.Completer completer = new Completers.Completer(environment);
        List<Candidate> candidates = new ArrayList<Candidate>();

        completer.complete(null, new TestParsedLine("", 0, Collections.singletonList("")), candidates);

        assertThat(candidateValues(candidates)).contains("commit", "ci").doesNotContain("_internal");
        assertThat(candidates)
                .filteredOn(candidate -> "commit".equals(candidate.value()))
                .singleElement()
                .extracting(Candidate::descr)
                .isEqualTo("record changes");

        candidates.clear();
        completer.complete(null, new TestParsedLine("commit -", 1, Arrays.asList("commit", "-")), candidates);

        assertThat(candidates)
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.value()).isEqualTo("--message");
                    assertThat(candidate.group()).isEqualTo("options");
                    assertThat(candidate.descr()).isEqualTo("commit message");
                });

        candidates.clear();
        completer.complete(null, new TestParsedLine("commit --message ", 2, Arrays.asList("commit", "--message", "")), candidates);

        assertThat(candidateValues(candidates)).containsExactly("initial commit", "metadata update");
    }

    private static List<String> candidateValues(final List<Candidate> candidates) {
        List<String> values = new ArrayList<String>();
        for (Candidate candidate : candidates) {
            values.add(candidate.value());
        }
        return values;
    }

    private static final class CompletionEnvironment implements Completers.CompletionEnvironment {

        private final Map<String, List<Completers.CompletionData>> completions;

        private CompletionEnvironment() {
            completions = new HashMap<String, List<Completers.CompletionData>>();
            completions.put("commit", Arrays.asList(
                    new Completers.CompletionData(null, "record changes", null, null),
                    new Completers.CompletionData(
                            Collections.singletonList("--message"),
                            "commit message",
                            "messages",
                            null)));
        }

        @Override
        public Map<String, List<Completers.CompletionData>> getCompletions() {
            return completions;
        }

        @Override
        public Set<String> getCommands() {
            return new HashSet<String>(Arrays.asList("commit", "_internal"));
        }

        @Override
        public String resolveCommand(final String command) {
            if ("ci".equals(command)) {
                return "commit";
            }
            return command;
        }

        @Override
        public String commandName(final String command) {
            if ("commit".equals(command)) {
                return "ci";
            }
            return command;
        }

        @Override
        public Object evaluate(final LineReader reader, final ParsedLine line, final String function) {
            if ("messages".equals(function)) {
                return Arrays.asList("initial commit", "metadata update");
            }
            return Boolean.FALSE;
        }
    }

    private static final class TestParsedLine implements ParsedLine {

        private final String line;
        private final int wordIndex;
        private final List<String> words;

        private TestParsedLine(final String line, final int wordIndex, final List<String> words) {
            this.line = line;
            this.wordIndex = wordIndex;
            this.words = words;
        }

        @Override
        public String word() {
            return words.get(wordIndex);
        }

        @Override
        public int wordCursor() {
            return word().length();
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
