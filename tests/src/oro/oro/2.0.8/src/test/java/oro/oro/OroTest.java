/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package oro.oro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.oro.io.AwkFilenameFilter;
import org.apache.oro.io.GlobFilenameFilter;
import org.apache.oro.io.Perl5FilenameFilter;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.MatchActionProcessor;
import org.apache.oro.text.PatternCacheLRU;
import org.apache.oro.text.awk.AwkCompiler;
import org.apache.oro.text.awk.AwkMatcher;
import org.apache.oro.text.awk.AwkStreamInput;
import org.apache.oro.text.perl.MalformedPerl5PatternException;
import org.apache.oro.text.perl.Perl5Util;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.MatchResult;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternMatcherInput;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.apache.oro.text.regex.Perl5Substitution;
import org.apache.oro.text.regex.StringSubstitution;
import org.apache.oro.text.regex.Util;
import org.apache.oro.util.CacheFIFO;
import org.apache.oro.util.CacheFIFO2;
import org.apache.oro.util.CacheLRU;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OroTest {
    @TempDir
    File temporaryDirectory;

    @Test
    void perl5CompilerAndMatcherSupportCapturesOffsetsAndOptions() throws MalformedPatternException {
        Perl5Compiler compiler = new Perl5Compiler();
        Perl5Matcher matcher = new Perl5Matcher();
        Pattern ticketPattern = compiler.compile("([a-z]+)-(\\d+)", Perl5Compiler.CASE_INSENSITIVE_MASK);

        assertThat(ticketPattern.getPattern()).isEqualTo("([a-z]+)-(\\d+)");
        assertThat(matcher.contains("prefix BUG-2048 suffix", ticketPattern)).isTrue();

        MatchResult match = matcher.getMatch();
        assertThat(match.groups()).isEqualTo(3);
        assertThat(match.group(0)).isEqualTo("BUG-2048");
        assertThat(match.group(1)).isEqualTo("BUG");
        assertThat(match.group(2)).isEqualTo("2048");
        assertThat(match.beginOffset(0)).isEqualTo(7);
        assertThat(match.endOffset(0)).isEqualTo(15);
        assertThat(match.length()).isEqualTo("BUG-2048".length());

        Pattern extendedMultilinePattern = compiler.compile(
                "^ name \\s* = \\s* ([a-z]+) # trailing comment",
                Perl5Compiler.EXTENDED_MASK | Perl5Compiler.MULTILINE_MASK);
        assertThat(matcher.contains("ignored\nname = oro\n", extendedMultilinePattern)).isTrue();
        assertThat(matcher.getMatch().group(1)).isEqualTo("oro");

        Pattern singlelinePattern = compiler.compile("begin.*end", Perl5Compiler.SINGLELINE_MASK);
        assertThat(matcher.matches("begin\nend", singlelinePattern)).isTrue();
        assertThat(Perl5Compiler.quotemeta("a+b*(c)")).isEqualTo("a\\+b\\*\\(c\\)");
    }

    @Test
    void patternMatcherInputTracksProgressAcrossRepeatedContainsCalls() throws MalformedPatternException {
        Perl5Compiler compiler = new Perl5Compiler();
        Perl5Matcher matcher = new Perl5Matcher();
        Pattern wordPattern = compiler.compile("([A-Z]+):(\\d+)");
        PatternMatcherInput input = new PatternMatcherInput("skip AA:10 middle BB:20 done");

        assertThat(input.length()).isEqualTo(28);
        assertThat(input.charAt(5)).isEqualTo('A');
        assertThat(input.substring(0, 4)).isEqualTo("skip");

        assertThat(matcher.contains(input, wordPattern)).isTrue();
        assertThat(matcher.getMatch().group(1)).isEqualTo("AA");
        assertThat(input.preMatch()).isEqualTo("skip ");
        assertThat(input.match()).isEqualTo("AA:10");
        assertThat(input.postMatch()).isEqualTo(" middle BB:20 done");
        assertThat(input.getMatchBeginOffset()).isEqualTo(5);
        assertThat(input.getMatchEndOffset()).isEqualTo(10);

        assertThat(matcher.contains(input, wordPattern)).isTrue();
        assertThat(matcher.getMatch().group(2)).isEqualTo("20");
        assertThat(input.getCurrentOffset()).isEqualTo(input.getMatchEndOffset());
        assertThat(matcher.contains(input, wordPattern)).isFalse();
        assertThat(input.endOfInput()).isTrue();

        char[] prefixedInput = "xxPREFIX-suffix".toCharArray();
        input.setInput(prefixedInput, 2, prefixedInput.length - 2);
        assertThat(matcher.matchesPrefix(input, compiler.compile("PREFIX"))).isTrue();
        assertThat(matcher.matchesPrefix("xxPREFIX".toCharArray(), compiler.compile("PREFIX"), 2)).isTrue();
    }

    @Test
    void utilSplitAndSubstituteHandleCollectionsLiteralAndInterpolatedReplacements()
            throws MalformedPatternException {
        Perl5Compiler compiler = new Perl5Compiler();
        Perl5Matcher matcher = new Perl5Matcher();
        Pattern commaPattern = compiler.compile("\\s*,\\s*");
        List<String> fields = new ArrayList<>();

        Util.split(fields, matcher, commaPattern, "alpha, beta,gamma");

        assertThat(fields).containsExactly("alpha", "beta", "gamma");

        Pattern propertyPattern = compiler.compile("([a-z]+)=(\\d+)");
        String substituted = Util.substitute(
                matcher,
                propertyPattern,
                new Perl5Substitution("$1<$2>"),
                "x=1 y=22 z=333",
                Util.SUBSTITUTE_ALL);
        assertThat(substituted).isEqualTo("x<1> y<22> z<333>");

        String limited = Util.substitute(
                matcher,
                compiler.compile("red"),
                new StringSubstitution("blue"),
                "red red red",
                2);
        assertThat(limited).isEqualTo("blue blue red");
    }

    @Test
    void perl5UtilParsesPerlStyleMatchSubstitutionAndSplitExpressions() {
        Perl5Util util = new Perl5Util(new PatternCacheLRU(4));

        assertThat(util.match("m#^(\\w+):(\\d+)$#", "item:42")).isTrue();
        assertThat(util.group(0)).isEqualTo("item:42");
        assertThat(util.group(1)).isEqualTo("item");
        assertThat(util.group(2)).isEqualTo("42");
        assertThat(util.beginOffset(2)).isEqualTo(5);
        assertThat(util.endOffset(2)).isEqualTo(7);

        String replaced = util.substitute("s/(\\w+)=(\\d+)/$1:$2/g", "a=1 b=22");
        assertThat(replaced).isEqualTo("a:1 b:22");

        List<String> colors = new ArrayList<>();
        util.split(colors, "/\\s*,\\s*/", "red, green,blue", Perl5Util.SPLIT_ALL);
        assertThat(colors).containsExactly("red", "green", "blue");

        assertThatThrownBy(() -> util.match("not-a-delimited-expression", "input"))
                .isInstanceOf(MalformedPerl5PatternException.class);
    }

    @Test
    void globCompilerPatternCacheAndFilenameFiltersCoverFileStyleMatching() throws MalformedPatternException {
        Perl5Matcher matcher = new Perl5Matcher();
        GlobCompiler globCompiler = new GlobCompiler();
        Pattern javaGlob = globCompiler.compile("*.java");
        Pattern dataGlob = globCompiler.compile("data-??.csv", GlobCompiler.CASE_INSENSITIVE_MASK);

        assertThat(matcher.matches("OroTest.java", javaGlob)).isTrue();
        assertThat(matcher.matches("OroTest.class", javaGlob)).isFalse();
        assertThat(matcher.matches("DATA-01.CSV", dataGlob)).isTrue();
        assertThat(GlobCompiler.globToPerl5("file?.txt".toCharArray(), GlobCompiler.DEFAULT_MASK))
                .contains("file")
                .contains("txt");

        PatternCacheLRU cache = new PatternCacheLRU(2, new Perl5Compiler());
        Pattern firstLookup = cache.getPattern("oro", Perl5Compiler.CASE_INSENSITIVE_MASK);
        Pattern secondLookup = cache.getPattern("oro", Perl5Compiler.CASE_INSENSITIVE_MASK);
        cache.getPattern("alpha");
        cache.getPattern("beta");

        assertThat(secondLookup).isSameAs(firstLookup);
        assertThat(cache.capacity()).isEqualTo(2);
        assertThat(cache.size()).isLessThanOrEqualTo(2);
        assertThat(matcher.matches("ORO", firstLookup)).isTrue();

        assertThat(new GlobFilenameFilter("*.java").accept(temporaryDirectory, "Example.java")).isTrue();
        assertThat(new GlobFilenameFilter("*.java").accept(temporaryDirectory, "Example.txt")).isFalse();
        assertThat(new Perl5FilenameFilter(".*\\.txt").accept(temporaryDirectory, "notes.txt")).isTrue();
        assertThat(new AwkFilenameFilter(".*\\.log").accept(temporaryDirectory, "server.log")).isTrue();
    }

    @Test
    void genericCachesApplyTheirEvictionPolicies() {
        CacheFIFO fifoCache = new CacheFIFO(2);
        fifoCache.addElement("first", "alpha");
        fifoCache.addElement("second", "beta");
        assertThat(fifoCache.getElement("first")).isEqualTo("alpha");

        fifoCache.addElement("third", "gamma");

        assertThat(fifoCache.getElement("first")).isNull();
        assertThat(fifoCache.getElement("second")).isEqualTo("beta");
        assertThat(fifoCache.getElement("third")).isEqualTo("gamma");
        assertThat(fifoCache.size()).isEqualTo(2);
        assertThat(fifoCache.capacity()).isEqualTo(2);

        CacheFIFO2 secondChanceCache = new CacheFIFO2(2);
        secondChanceCache.addElement("first", "alpha");
        secondChanceCache.addElement("second", "beta");
        assertThat(secondChanceCache.getElement("first")).isEqualTo("alpha");

        secondChanceCache.addElement("third", "gamma");

        assertThat(secondChanceCache.getElement("first")).isEqualTo("alpha");
        assertThat(secondChanceCache.getElement("second")).isNull();
        assertThat(secondChanceCache.getElement("third")).isEqualTo("gamma");

        CacheLRU leastRecentlyUsedCache = new CacheLRU(2);
        leastRecentlyUsedCache.addElement("first", "alpha");
        leastRecentlyUsedCache.addElement("second", "beta");
        assertThat(leastRecentlyUsedCache.getElement("first")).isEqualTo("alpha");

        leastRecentlyUsedCache.addElement("third", "gamma");

        assertThat(leastRecentlyUsedCache.getElement("first")).isEqualTo("alpha");
        assertThat(leastRecentlyUsedCache.getElement("second")).isNull();
        assertThat(leastRecentlyUsedCache.getElement("third")).isEqualTo("gamma");

        leastRecentlyUsedCache.addElement("first", "updated");

        assertThat(leastRecentlyUsedCache.getElement("first")).isEqualTo("updated");
        assertThat(leastRecentlyUsedCache.size()).isEqualTo(2);
    }

    @Test
    void awkCompilerMatcherAndStreamInputHandleAwkStyleRegularExpressions()
            throws IOException, MalformedPatternException {
        AwkCompiler compiler = new AwkCompiler();
        AwkMatcher matcher = new AwkMatcher();
        Pattern recordPattern = compiler.compile("[A-Z][a-z]+[ \t]+[0-9]+");
        Pattern caseInsensitivePattern = compiler.compile("warning", AwkCompiler.CASE_INSENSITIVE_MASK);

        assertThat(matcher.contains("INFO: Alice 42 connected", recordPattern)).isTrue();
        assertThat(matcher.getMatch().group(0)).isEqualTo("Alice 42");
        assertThat(matcher.matchesPrefix("Alice 42".toCharArray(), recordPattern)).isTrue();
        assertThat(matcher.matches("Warning", caseInsensitivePattern)).isTrue();

        AwkStreamInput streamInput = new AwkStreamInput(new StringReader("first line\nsecond Alice 99 line\n"), 8);
        assertThat(matcher.contains(streamInput, recordPattern)).isTrue();
        assertThat(matcher.getMatch().toString()).isEqualTo("Alice 99");
    }

    @Test
    void matchActionProcessorDispatchesLineActionsWithFields() throws IOException, MalformedPatternException {
        MatchActionProcessor processor = new MatchActionProcessor();
        StringWriter output = new StringWriter();
        List<Integer> matchedLines = new ArrayList<>();

        processor.setFieldSeparator("\\|");
        processor.addAction("^ERROR", info -> {
            matchedLines.add(Integer.valueOf(info.lineNumber));
            info.output.println(info.fields.get(0) + ":" + info.fields.get(1) + ":" + info.match.group(0));
        });

        processor.processMatches(new StringReader("INFO|started\nERROR|failed\nWARN|slow\n"), output);

        assertThat(matchedLines).containsExactly(Integer.valueOf(2));
        assertThat(output).hasToString("ERROR:failed:ERROR\n");
    }

    @Test
    void matchActionProcessorDefaultActionsCopyMatchingLines() throws IOException, MalformedPatternException {
        MatchActionProcessor processor = new MatchActionProcessor();
        StringWriter output = new StringWriter();

        processor.addAction("^WARN");
        processor.addAction("^ERROR", Perl5Compiler.CASE_INSENSITIVE_MASK);
        processor.processMatches(new StringReader("INFO|started\nWARN|slow\nerror|failed\n"), output);

        assertThat(output).hasToString("WARN|slow\nerror|failed\n");
    }

    @Test
    void matchActionProcessorSupportsByteStreamsWithExplicitEncoding() throws IOException, MalformedPatternException {
        MatchActionProcessor processor = new MatchActionProcessor();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        processor.addAction("caf\u00e9", info -> info.output.println(info.lineNumber + ":matched"));
        processor.processMatches(
                new ByteArrayInputStream("plain\ncaf\u00e9\n".getBytes(StandardCharsets.UTF_8)),
                output,
                StandardCharsets.UTF_8.name());

        assertThat(output).hasToString("2:matched\n");
    }
}
