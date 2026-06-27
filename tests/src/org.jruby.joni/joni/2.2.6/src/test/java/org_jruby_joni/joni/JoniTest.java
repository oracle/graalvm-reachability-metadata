/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jruby_joni.joni;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.jcodings.specific.UTF8Encoding;
import org.joni.Matcher;
import org.joni.NameEntry;
import org.joni.Option;
import org.joni.Regex;
import org.joni.Region;
import org.joni.Syntax;
import org.joni.exception.SyntaxException;
import org.junit.jupiter.api.Test;

public class JoniTest {
    @Test
    void searchFindsMatchesAndExposesNumberedCaptureRegions() {
        Regex regex = regex("([A-Z]{2})(\\d{3})(?:-([a-z]+))?");
        byte[] input = bytes("xx AB123-test yy");
        int expectedStart = bytes("xx ").length;

        Matcher matcher = regex.matcher(input);
        int matchStart = matcher.search(0, input.length, Option.DEFAULT);
        Region region = matcher.getEagerRegion();

        assertThat(matchStart).isEqualTo(expectedStart);
        assertThat(matcher.getBegin()).isEqualTo(expectedStart);
        assertThat(matcher.getEnd()).isEqualTo(expectedStart + bytes("AB123-test").length);
        assertThat(regex.numberOfCaptures()).isEqualTo(3);
        assertThat(region.getNumRegs()).isEqualTo(4);
        assertThat(extract(input, region, 0)).isEqualTo("AB123-test");
        assertThat(extract(input, region, 1)).isEqualTo("AB");
        assertThat(extract(input, region, 2)).isEqualTo("123");
        assertThat(extract(input, region, 3)).isEqualTo("test");

        Region clone = region.clone();
        clone.setBeg(1, 0);
        clone.setEnd(1, 2);
        assertThat(extract(input, clone, 1)).isEqualTo("xx");
        assertThat(extract(input, region, 1)).isEqualTo("AB");
    }

    @Test
    void matchAnchorsThePatternAtTheRequestedPosition() {
        Regex regex = regex("\\A([a-z]+)(\\d+)\\z");
        byte[] matching = bytes("abc123");
        byte[] nonMatching = bytes("abc123x");

        Matcher matcher = regex.matcher(matching);
        int matchLength = matcher.match(0, matching.length, Option.DEFAULT);
        Region region = matcher.getEagerRegion();

        assertThat(matchLength).isEqualTo(matching.length);
        assertThat(extract(matching, region, 1)).isEqualTo("abc");
        assertThat(extract(matching, region, 2)).isEqualTo("123");
        assertThat(regex.matcher(nonMatching).match(0, nonMatching.length, Option.DEFAULT))
                .isEqualTo(Matcher.FAILED);
    }

    @Test
    void lookaroundAssertionsConstrainMatchesWithoutConsumingText() {
        Regex regex = regex("(?<=USD )\\d+(?![\\dA-Z])");
        byte[] input = bytes("EUR 100 USD 123 USD 456X USD 789.");
        int firstAmount = bytes("EUR 100 USD ").length;
        int secondAmount = bytes("EUR 100 USD 123 USD 456X USD ").length;

        Matcher matcher = regex.matcher(input);
        int matchStart = matcher.search(0, input.length, Option.DEFAULT);
        Region region = matcher.getEagerRegion();

        assertThat(matchStart).isEqualTo(firstAmount);
        assertThat(matcher.getBegin()).isEqualTo(firstAmount);
        assertThat(extract(input, region, 0)).isEqualTo("123");

        assertThat(matcher.search(matcher.getEnd(), input.length, Option.DEFAULT))
                .isEqualTo(secondAmount);
        assertThat(extract(input, matcher.getEagerRegion(), 0)).isEqualTo("789");
    }

    @Test
    void rubyNamedGroupsCanBeResolvedIteratedAndUsedAsBackReferences() {
        Regex regex = regex("\\A(?<token>[A-Za-z]+)-\\k<token>\\z");
        byte[] good = bytes("repeat-repeat");
        byte[] bad = bytes("repeat-repeal");
        byte[] name = bytes("token");

        Matcher matcher = regex.matcher(good);
        assertThat(matcher.match(0, good.length, Option.DEFAULT)).isEqualTo(good.length);
        Region region = matcher.getEagerRegion();

        assertThat(regex.numberOfNames()).isEqualTo(1);
        assertThat(regex.nameToBackrefNumber(name, 0, name.length, region)).isEqualTo(1);
        assertThat(extract(good, region, 1)).isEqualTo("repeat");
        assertThat(namedBackReferences(regex)).containsExactly("token=[1]");
        assertThat(regex.matcher(bad).match(0, bad.length, Option.DEFAULT))
                .isEqualTo(Matcher.FAILED);
    }

    @Test
    void optionsControlCaseSensitivityLineAnchorsAndDotNewlineMatching() {
        Regex caseInsensitiveRegex = regex("^status: (ok|warn).+$", Option.IGNORECASE, Syntax.RUBY);
        byte[] input = bytes("header\nSTATUS: Warn disk\nfooter");
        int expectedStart = bytes("header\n").length;

        Matcher matcher = caseInsensitiveRegex.matcher(input);
        int matchStart = matcher.search(0, input.length, Option.DEFAULT);
        Region region = matcher.getEagerRegion();

        assertThat(Option.isIgnoreCase(caseInsensitiveRegex.getOptions())).isTrue();
        assertThat(matchStart).isEqualTo(expectedStart);
        assertThat(extract(input, region, 0)).isEqualTo("STATUS: Warn disk");
        assertThat(extract(input, region, 1)).isEqualTo("Warn");

        Regex multilineRegex = regex("start.+finish", Option.MULTILINE, Syntax.RUBY);
        byte[] acrossLines = bytes("start\nmiddle\nfinish");
        assertThat(multilineRegex.matcher(acrossLines)
                .search(0, acrossLines.length, Option.DEFAULT)).isEqualTo(0);
        assertThat(Option.isMultiline(multilineRegex.getOptions())).isTrue();

        Regex singleLineRegex = regex("^STATUS", Option.SINGLELINE, Syntax.RUBY);
        assertThat(singleLineRegex.matcher(input).search(0, input.length, Option.DEFAULT))
                .isEqualTo(Matcher.FAILED);
        assertThat(Option.isSingleline(singleLineRegex.getOptions())).isTrue();
    }

    @Test
    void utf8EncodingMatchesUnicodeLiteralsAndReportsByteOffsets() {
        Regex regex = regex("café\\s+(世界)");
        byte[] input = bytes("prefix café 世界 suffix");
        int expectedStart = bytes("prefix ").length;
        int expectedGroupStart = bytes("prefix café ").length;

        Matcher matcher = regex.matcher(input);
        int matchStart = matcher.search(0, input.length, Option.DEFAULT);
        Region region = matcher.getEagerRegion();

        assertThat(regex.getEncoding()).isSameAs(UTF8Encoding.INSTANCE);
        assertThat(matchStart).isEqualTo(expectedStart);
        assertThat(region.getBeg(1)).isEqualTo(expectedGroupStart);
        assertThat(extract(input, region, 0)).isEqualTo("café 世界");
        assertThat(extract(input, region, 1)).isEqualTo("世界");
    }

    @Test
    void matcherWithoutRegionStillReportsSearchBoundsForRepeatedRangeSearches() {
        Regex regex = regex("\\bcat\\b");
        byte[] input = bytes("concatenate cat category cat");
        int firstCat = "concatenate cat category cat".indexOf(" cat ") + 1;
        int secondCat = "concatenate cat category cat".lastIndexOf("cat");

        Matcher matcher = regex.matcherNoRegion(input, 0, input.length);
        assertThat(matcher.search(0, input.length, Option.DEFAULT)).isEqualTo(firstCat);
        assertThat(matcher.getBegin()).isEqualTo(firstCat);
        assertThat(matcher.getEnd()).isEqualTo(firstCat + bytes("cat").length);
        assertThat(matcher.getRegion()).isNull();

        assertThat(matcher.search(matcher.getEnd(), input.length, Option.DEFAULT))
                .isEqualTo(secondCat);
        assertThat(matcher.getBegin()).isEqualTo(secondCat);
        assertThat(matcher.getEnd()).isEqualTo(secondCat + bytes("cat").length);
    }

    @Test
    void regexStoresApplicationOptionsAndUserObject() {
        Regex regex = regex("color");
        Object marker = new Object();

        regex.setUserOptions(Option.IGNORECASE | Option.FIND_LONGEST);
        regex.setUserObject(marker);

        assertThat(regex.getUserOptions()).isEqualTo(Option.IGNORECASE | Option.FIND_LONGEST);
        assertThat(regex.getUserObject()).isSameAs(marker);
        assertThat(regex.isLinear()).isTrue();
        assertThat(regex.optimizeInfoToString()).contains("optimize");
    }

    @Test
    void invalidPatternThrowsSyntaxException() {
        assertThatThrownBy(() -> regex("([unterminated"))
                .isInstanceOf(SyntaxException.class);
    }

    private static Regex regex(String pattern) {
        return regex(pattern, Option.DEFAULT, Syntax.RUBY);
    }

    private static Regex regex(String pattern, int options, Syntax syntax) {
        byte[] patternBytes = bytes(pattern);
        return new Regex(patternBytes, 0, patternBytes.length, options,
                UTF8Encoding.INSTANCE, syntax);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(UTF_8);
    }

    private static String extract(byte[] input, Region region, int group) {
        int begin = region.getBeg(group);
        return new String(input, begin, region.getEnd(group) - begin, UTF_8);
    }

    private static List<String> namedBackReferences(Regex regex) {
        List<String> entries = new ArrayList<>();
        Iterator<NameEntry> iterator = regex.namedBackrefIterator();
        while (iterator.hasNext()) {
            NameEntry entry = iterator.next();
            String name = new String(entry.name, entry.nameP, entry.nameEnd - entry.nameP, UTF_8);
            entries.add(name + "=" + Arrays.toString(entry.getBackRefs()));
        }
        return entries;
    }
}
