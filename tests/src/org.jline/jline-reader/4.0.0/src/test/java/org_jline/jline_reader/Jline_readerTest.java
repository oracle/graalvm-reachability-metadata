/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jline.jline_reader;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.reader.Candidate;
import org.jline.reader.EOFError;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.impl.BufferImpl;
import org.jline.reader.impl.DefaultExpander;
import org.jline.reader.impl.DefaultHighlighter;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.SimpleMaskingCallback;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.EnumCompleter;
import org.jline.reader.impl.completer.FileNameCompleter;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Jline_readerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultParserHandlesQuotingEscapingAndCompletionMetadata() {
        DefaultParser parser = new DefaultParser()
                .quoteChars(new char[] {'\'', '"'})
                .escapeChars(new char[] {'\\'})
                .eofOnEscapedNewLine(true);
        String line = "deploy \"two words\" plain\\ value";

        ParsedLine parsedLine = parser.parse(line, line.length(), Parser.ParseContext.COMPLETE);

        assertThat(parsedLine.line()).isEqualTo(line);
        assertThat(parsedLine.cursor()).isEqualTo(line.length());
        assertThat(parsedLine.words()).containsExactly("deploy", "two words", "plain value");
        assertThat(parsedLine.wordIndex()).isEqualTo(2);
        assertThat(parsedLine.word()).isEqualTo("plain value");
        assertThat(parsedLine.wordCursor()).isEqualTo("plain value".length());
        assertThat(parser.getQuoteChars()).contains('"');
        assertThat(parser.getEscapeChars()).contains('\\');
    }

    @Test
    void defaultParserReportsIncompleteInputWhenConfiguredForMultilineEditing() {
        DefaultParser parser = new DefaultParser()
                .eofOnUnclosedQuote(true)
                .eofOnUnclosedBracket(DefaultParser.Bracket.ROUND, DefaultParser.Bracket.CURLY);

        String unclosedQuote = "echo \"unterminated";
        String unclosedBracket = "call(first, second";

        assertThatThrownBy(() -> parser.parse(unclosedQuote, unclosedQuote.length(), Parser.ParseContext.ACCEPT_LINE))
                .isInstanceOf(EOFError.class);
        assertThatThrownBy(() -> parser.parse(unclosedBracket, unclosedBracket.length(), Parser.ParseContext.ACCEPT_LINE))
                .isInstanceOf(EOFError.class);
    }

    @Test
    void completersContributeCandidatesForStringsEnumsAggregateAndArguments() {
        DefaultParser parser = new DefaultParser();
        ParsedLine firstWord = parser.parse("", 0, Parser.ParseContext.COMPLETE);
        List<Candidate> aggregateCandidates = new ArrayList<>();
        AggregateCompleter aggregateCompleter = new AggregateCompleter(
                new StringsCompleter("status", "checkout"),
                new EnumCompleter(CommandMode.class),
                NullCompleter.INSTANCE);

        aggregateCompleter.complete(null, firstWord, aggregateCandidates);

        assertThat(candidateValues(aggregateCandidates))
                .contains("status", "checkout", "fast_mode", "safe_mode");

        ParsedLine commandLine = parser.parse("git checkout ", "git checkout ".length(), Parser.ParseContext.COMPLETE);
        List<Candidate> argumentCandidates = new ArrayList<>();
        ArgumentCompleter argumentCompleter = new ArgumentCompleter(
                new StringsCompleter("git"),
                new StringsCompleter("checkout", "commit"),
                new StringsCompleter("main", "feature/native"));

        argumentCompleter.complete(null, commandLine, argumentCandidates);

        assertThat(candidateValues(argumentCandidates)).containsExactly("main", "feature/native");
        assertThat(argumentCompleter.getCompleters()).hasSize(3);
        assertThat(argumentCompleter.isStrict()).isTrue();
    }

    @Test
    void bufferSupportsCursorEditingDeletionAndCopying() {
        BufferImpl buffer = new BufferImpl();

        buffer.write("hello world");
        assertThat(buffer.toString()).isEqualTo("hello world");
        assertThat(buffer.cursor()).isEqualTo("hello world".length());

        assertThat(buffer.move(-5)).isEqualTo(-5);
        buffer.write("native ");
        assertThat(buffer.toString()).isEqualTo("hello native world");
        assertThat(buffer.upToCursor()).isEqualTo("hello native ");

        assertThat(buffer.backspace(7)).isEqualTo(7);
        assertThat(buffer.toString()).isEqualTo("hello world");
        assertThat(buffer.delete(5)).isEqualTo(5);
        assertThat(buffer.toString()).isEqualTo("hello ");

        BufferImpl copy = buffer.copy();
        buffer.write("again");
        assertThat(copy.toString()).isEqualTo("hello ");
        copy.copyFrom(buffer);
        assertThat(copy.toString()).isEqualTo("hello again");
    }

    @Test
    void historyNavigatesPersistsAndExpandsPreviousEvents() throws Exception {
        DefaultHistory history = new DefaultHistory();
        Instant firstTimestamp = Instant.parse("2024-01-01T00:00:00Z");
        Instant secondTimestamp = Instant.parse("2024-01-01T00:00:01Z");

        history.add(firstTimestamp, "git status");
        history.add(secondTimestamp, "git checkout main");

        assertThat(history).hasSize(2);
        assertThat(history.first()).isZero();
        assertThat(history.last()).isEqualTo(1);
        assertThat(history.get(0)).isEqualTo("git status");
        assertThat(history.moveToFirst()).isTrue();
        assertThat(history.current()).isEqualTo("git status");
        assertThat(history.next()).isTrue();
        assertThat(history.current()).isEqualTo("git checkout main");
        history.moveToEnd();

        String expanded = new DefaultExpander().expandHistory(history, "sudo !!");
        assertThat(expanded).isEqualTo("sudo git checkout main");

        Path historyFile = temporaryDirectory.resolve("jline-history.txt");
        try (Terminal terminal = TerminalBuilder.builder()
                .name("jline-history-test")
                .system(false)
                .dumb(true)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .history(history)
                    .build();
            history.attach(reader);
            history.write(historyFile, false);
        }

        DefaultHistory reloaded = new DefaultHistory();
        try (Terminal terminal = TerminalBuilder.builder()
                .name("jline-history-reload-test")
                .system(false)
                .dumb(true)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .history(reloaded)
                    .build();
            reloaded.attach(reader);
            reloaded.read(historyFile, false);
        }

        assertThat(historyLines(reloaded)).containsExactly("git status", "git checkout main");
        assertThat(reloaded.iterator(0).next().time()).isNotNull();
    }

    @Test
    void lineReaderReadsScriptedInputWithConfiguredTerminalHistoryAndVariables() throws Exception {
        byte[] input = "hello native image\n".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DefaultHistory history = new DefaultHistory();

        try (Terminal terminal = TerminalBuilder.builder()
                .name("jline-reader-test")
                .system(false)
                .dumb(true)
                .streams(new ByteArrayInputStream(input), output)
                .encoding(StandardCharsets.UTF_8)
                .size(new Size(80, 24))
                .build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("jline-reader-tests")
                    .parser(new DefaultParser())
                    .completer(new StringsCompleter("hello", "help"))
                    .history(history)
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%M> ")
                    .build();

            reader.setVariable(LineReader.LIST_MAX, 25);
            reader.setTailTip("tail-tip");
            assertThat(reader.getTailTip()).isEqualTo("tail-tip");
            reader.setAutosuggestion(LineReader.SuggestionType.HISTORY);
            String line = reader.readLine("prompt> ");

            assertThat(line).isEqualTo("hello native image");
            assertThat(reader.getAppName()).isEqualTo("jline-reader-tests");
            assertThat(reader.isSet(LineReader.Option.DISABLE_EVENT_EXPANSION)).isTrue();
            assertThat(reader.getVariable(LineReader.LIST_MAX)).isEqualTo(25);
            assertThat(reader.getTailTip()).isEmpty();
            assertThat(reader.getAutosuggestion()).isEqualTo(LineReader.SuggestionType.HISTORY);
            assertThat(historyLines(history)).containsExactly("hello native image");
        }

        assertThat(output.toString(StandardCharsets.UTF_8)).contains("prompt> ");
    }

    @Test
    void keyMapTranslatesDisplaysBindsAndReadsTerminalInput() throws Exception {
        assertThat(KeyMap.ctrl('A')).isEqualTo("\u0001");
        assertThat(KeyMap.alt('x')).isEqualTo("\u001Bx");
        assertThat(KeyMap.display(KeyMap.ctrl('C'))).isEqualTo("\"^C\"");
        assertThat(KeyMap.range("a-c")).containsExactly("a", "b", "c");

        KeyMap<String> keyMap = new KeyMap<>();
        keyMap.bind("insert-x", "x");
        keyMap.bindIfNotBound("ignored", "x");
        keyMap.bind("control-a", KeyMap.ctrl('A'));

        assertThat(keyMap.getBound("x")).isEqualTo("insert-x");
        assertThat(keyMap.getBound(KeyMap.ctrl('A'))).isEqualTo("control-a");
        assertThat(keyMap.getBoundKeys()).containsEntry("x", "insert-x");

        try (Terminal terminal = TerminalBuilder.builder()
                .name("jline-binding-reader-test")
                .system(false)
                .dumb(true)
                .streams(new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), new ByteArrayOutputStream())
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            BindingReader bindingReader = new BindingReader(terminal.reader());

            assertThat(bindingReader.readBinding(keyMap)).isEqualTo("insert-x");
            assertThat(bindingReader.getLastBinding()).isEqualTo("x");
        }
    }

    @Test
    void fileNameCompleterContributesFileAndDirectoryCandidates() throws Exception {
        Path workingDirectory = temporaryDirectory.resolve("workspace");
        Path scriptsDirectory = workingDirectory.resolve("scripts");
        Files.createDirectories(scriptsDirectory);
        Files.writeString(workingDirectory.resolve("status.txt"), "ready", StandardCharsets.UTF_8);
        Files.writeString(scriptsDirectory.resolve("build.sh"), "echo build", StandardCharsets.UTF_8);

        FileNameCompleter completer = new FileNameCompleter();
        String separator = workingDirectory.getFileSystem().getSeparator();
        String topLevelPrefix = workingDirectory.toString() + separator;
        String scriptsCandidateValue = scriptsDirectory.toString() + separator;
        String statusCandidateValue = workingDirectory.resolve("status.txt").toString();
        String nestedCandidateValue = scriptsDirectory.resolve("build.sh").toString();
        try (Terminal terminal = TerminalBuilder.builder()
                .name("jline-file-completer-test")
                .system(false)
                .dumb(true)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(completer)
                    .option(LineReader.Option.AUTO_PARAM_SLASH, true)
                    .option(LineReader.Option.AUTO_REMOVE_SLASH, true)
                    .build();
            DefaultParser parser = new DefaultParser().escapeChars(new char[0]);
            List<Candidate> topLevelCandidates = new ArrayList<>();

            String topLevelInput = topLevelPrefix + "s";
            completer.complete(
                    reader,
                    parser.parse(topLevelInput, topLevelInput.length(), Parser.ParseContext.COMPLETE),
                    topLevelCandidates);

            assertThat(candidateValues(topLevelCandidates)).contains(scriptsCandidateValue, statusCandidateValue);
            Candidate scriptsCandidate = candidateWithValue(topLevelCandidates, scriptsCandidateValue);
            assertThat(scriptsCandidate.complete()).isFalse();
            assertThat(scriptsCandidate.suffix()).isEqualTo(separator);
            assertThat(candidateWithValue(topLevelCandidates, statusCandidateValue).complete()).isTrue();

            List<Candidate> nestedCandidates = new ArrayList<>();
            completer.complete(
                    reader,
                    parser.parse(scriptsCandidateValue, scriptsCandidateValue.length(), Parser.ParseContext.COMPLETE),
                    nestedCandidates);

            assertThat(candidateValues(nestedCandidates)).containsExactly(nestedCandidateValue);
            assertThat(candidateWithValue(nestedCandidates, nestedCandidateValue).complete()).isTrue();
        }
    }

    @Test
    void defaultHighlighterStylesConfiguredErrorsWithoutChangingDisplayedText() throws Exception {
        String line = "ok ERR42 warning";
        int errorIndex = line.indexOf("warning");
        DefaultHighlighter highlighter = new DefaultHighlighter();
        highlighter.setErrorPattern(Pattern.compile("\\bERR\\w+\\b"));
        highlighter.setErrorIndex(errorIndex);

        try (Terminal terminal = TerminalBuilder.builder()
                .name("jline-highlighter-test")
                .system(false)
                .dumb(true)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .highlighter(highlighter)
                    .build();

            AttributedString highlighted = reader.getHighlighter().highlight(reader, line);

            assertThat(highlighted.toString()).isEqualTo(line);
            assertThat(highlighted.styleAt(line.indexOf("ERR42"))).isNotEqualTo(AttributedStyle.DEFAULT);
            assertThat(highlighted.styleAt(errorIndex)).isNotEqualTo(AttributedStyle.DEFAULT);
            assertThat(highlighted.styleAt(line.indexOf(' ', line.indexOf("ERR42")))).isEqualTo(AttributedStyle.DEFAULT);
        }
    }

    @Test
    void maskingCallbackAndCandidatesExposeDisplayHistoryAndSortingMetadata() {
        SimpleMaskingCallback maskingCallback = new SimpleMaskingCallback('*');
        Candidate visible = new Candidate("checkout", "checkout", "commands", "switch branches", " ", "co", true);
        Candidate hidden = new Candidate("commit");
        List<Candidate> candidates = new ArrayList<>(List.of(visible, hidden));

        candidates.sort(Comparator.naturalOrder());

        assertThat(maskingCallback.display("secret")).isEqualTo("******");
        assertThat(maskingCallback.history("secret")).isNull();
        assertThat(candidates).extracting(Candidate::value).containsExactly("checkout", "commit");
        assertThat(visible.displ()).isEqualTo("checkout");
        assertThat(visible.group()).isEqualTo("commands");
        assertThat(visible.descr()).isEqualTo("switch branches");
        assertThat(visible.suffix()).isEqualTo(" ");
        assertThat(visible.key()).isEqualTo("co");
        assertThat(visible.complete()).isTrue();
    }

    private static Candidate candidateWithValue(List<Candidate> candidates, String value) {
        return candidates.stream()
                .filter(candidate -> candidate.value().equals(value))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing candidate: " + value));
    }

    private static List<String> candidateValues(List<Candidate> candidates) {
        return candidates.stream().map(Candidate::value).toList();
    }

    private static List<String> historyLines(History history) {
        List<String> lines = new ArrayList<>();
        history.forEach(entry -> lines.add(entry.line()));
        return lines;
    }

    private enum CommandMode {
        FAST_MODE,
        SAFE_MODE
    }
}
