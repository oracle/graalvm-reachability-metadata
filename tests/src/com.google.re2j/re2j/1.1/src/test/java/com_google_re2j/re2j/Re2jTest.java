/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_re2j.re2j;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Re2jTest {
    @Test
    void supportsFullMatchHelpersAndCompileFlags() {
        assertThat(Pattern.matches("[a-z]+\\d{2}", new DelegatingCharSequence("build42")))
                .isTrue();
        assertThat(Pattern.matches("[a-z]+\\d{2}", "build7"))
                .isFalse();

        int flags = Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL;
        Pattern pattern = Pattern.compile("^error: (.+)$", flags);

        assertThat(pattern.flags()).isEqualTo(flags);
        assertThat(pattern.pattern()).isEqualTo("^error: (.+)$");
        assertThat(pattern.toString()).isEqualTo("^error: (.+)$");
        assertThat(pattern.groupCount()).isEqualTo(1);
        assertThat(pattern.matcher("header\nERROR: timed\nout\nfooter").find()).isTrue();
        assertThat(pattern.matches("ERROR: timed\nout")).isTrue();

        pattern.reset();
        assertThat(pattern.matcher("ERROR: after reset").matches()).isTrue();
    }

    @Test
    void supportsScopedInlineFlagsAndWordBoundaries() {
        Pattern marker = Pattern.compile("(?i:\\b(?:todo|fixme)\\b):\\s+([a-z]+)");
        Matcher matcher = marker.matcher("TODO: cleanup todoing: skip FixMe: refactor FIXME: RETRY");

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group()).isEqualTo("TODO: cleanup");
        assertThat(matcher.group(1)).isEqualTo("cleanup");

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group()).isEqualTo("FixMe: refactor");
        assertThat(matcher.group(1)).isEqualTo("refactor");

        assertThat(matcher.find()).isFalse();
    }

    @Test
    void findsMatchesAndReportsCapturingGroupsAndOffsets() {
        Pattern pattern = Pattern.compile("([A-Za-z]+)-(\\d+)(?:-([A-Za-z]+))?");
        Matcher matcher = pattern.matcher("id-42 next-7-beta");

        assertThat(matcher.pattern()).isSameAs(pattern);
        assertThat(matcher.groupCount()).isEqualTo(3);
        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group()).isEqualTo("id-42");
        assertThat(matcher.group(1)).isEqualTo("id");
        assertThat(matcher.group(2)).isEqualTo("42");
        assertThat(matcher.group(3)).isNull();
        assertThat(matcher.start()).isZero();
        assertThat(matcher.end()).isEqualTo(5);
        assertThat(matcher.start(2)).isEqualTo(3);
        assertThat(matcher.end(2)).isEqualTo(5);

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group()).isEqualTo("next-7-beta");
        assertThat(matcher.group(1)).isEqualTo("next");
        assertThat(matcher.group(2)).isEqualTo("7");
        assertThat(matcher.group(3)).isEqualTo("beta");
        assertThat(matcher.start()).isEqualTo(6);
        assertThat(matcher.end()).isEqualTo(17);
        assertThat(matcher.find()).isFalse();
    }

    @Test
    void supportsLookingAtFindFromIndexAndResettingInput() {
        Pattern pattern = Pattern.compile("[A-Z]{2}-\\d+");
        Matcher matcher = pattern.matcher("AB-12 text CD-34");

        assertThat(matcher.lookingAt()).isTrue();
        assertThat(matcher.matches()).isFalse();
        assertThat(matcher.find(8)).isTrue();
        assertThat(matcher.group()).isEqualTo("CD-34");
        assertThat(matcher.start()).isEqualTo(11);

        assertThat(matcher.reset().find()).isTrue();
        assertThat(matcher.group()).isEqualTo("AB-12");

        matcher.reset(new DelegatingCharSequence("EF-56"));
        assertThat(matcher.matches()).isTrue();
        assertThat(matcher.group()).isEqualTo("EF-56");
    }

    @Test
    void supportsReluctantQuantifiersForMinimalMatches() {
        String input = "<item>first</item><item>second</item>";

        Matcher greedyMatcher = Pattern.compile("<item>.*</item>").matcher(input);
        assertThat(greedyMatcher.find()).isTrue();
        assertThat(greedyMatcher.group()).isEqualTo(input);

        Matcher reluctantMatcher = Pattern.compile("<item>.*?</item>").matcher(input);
        assertThat(reluctantMatcher.find()).isTrue();
        assertThat(reluctantMatcher.group()).isEqualTo("<item>first</item>");
        assertThat(reluctantMatcher.find()).isTrue();
        assertThat(reluctantMatcher.group()).isEqualTo("<item>second</item>");
        assertThat(reluctantMatcher.find()).isFalse();
    }

    @Test
    void replacesAllFirstAndWithAppendReplacement() {
        Pattern assignment = Pattern.compile("([A-Za-z]+)=(\\d+)");

        assertThat(assignment.matcher("x=1;y=22").replaceAll("$1:<$2>"))
                .isEqualTo("x:<1>;y:<22>");
        assertThat(assignment.matcher("x=1;y=22").replaceFirst("$2@$1"))
                .isEqualTo("1@x;y=22");

        Matcher matcher = assignment.matcher("x=1;y=22;z=333");
        StringBuffer buffer = new java.lang.StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "$2@$1");
        }
        matcher.appendTail(buffer);

        assertThat(buffer).hasToString("1@x;22@y;333@z");
    }

    @Test
    void splitsInputWithDefaultAndExplicitLimits() {
        Pattern comma = Pattern.compile(",");

        assertThat(comma.split("alpha,beta,gamma"))
                .containsExactly("alpha", "beta", "gamma");
        assertThat(comma.split("alpha,beta,,", 0))
                .containsExactly("alpha", "beta");
        assertThat(comma.split("alpha,beta,gamma", 2))
                .containsExactly("alpha", "beta,gamma");
        assertThat(Pattern.compile("\\s*,\\s*").split("alpha, beta " + ",gamma"))
                .containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void quotesLiteralTextBeforeCompilation() {
        String literal = "a+b?(c)[d]\\end";
        Pattern quoted = Pattern.compile(Pattern.quote(literal));

        assertThat(quoted.matches(literal)).isTrue();
        assertThat(quoted.matcher("prefix " + literal + " suffix").find()).isTrue();
        assertThat(quoted.matches("aaabxcddend")).isFalse();
    }

    @Test
    void handlesUnicodeCharacterClassesAndCanDisableThem() {
        Pattern greekWord = Pattern.compile("^\\p{Greek}+$");
        Pattern notGreek = Pattern.compile("^\\P{Greek}+$");

        assertThat(greekWord.matches("\u03BB\u03A9\u03B2")).isTrue();
        assertThat(greekWord.matches("lambda")).isFalse();
        assertThat(notGreek.matches("lambda123")).isTrue();
        assertThat(notGreek.matches("\u03BBambda")).isFalse();

        assertThatThrownBy(() -> Pattern.compile("\\p{Greek}", Pattern.DISABLE_UNICODE_GROUPS))
                .isInstanceOf(PatternSyntaxException.class);
    }

    @Test
    void rejectsInvalidPatternsAndFlags() {
        assertThatThrownBy(() -> Pattern.compile("[unterminated"))
                .isInstanceOf(PatternSyntaxException.class)
                .satisfies(throwable -> {
                    PatternSyntaxException exception = (PatternSyntaxException) throwable;
                    assertThat(exception.getPattern()).isEqualTo("[unterminated");
                    assertThat(exception.getDescription()).isNotBlank();
                    assertThat(exception.getIndex()).isEqualTo(-1);
                });

        assertThatThrownBy(() -> Pattern.compile("(?<=a)b"))
                .isInstanceOf(PatternSyntaxException.class);
        assertThatThrownBy(() -> Pattern.compile("literal", 1 << 12))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Flags should only be a combination");
    }

    private static final class DelegatingCharSequence implements CharSequence {
        private final String value;

        private DelegatingCharSequence(String value) {
            this.value = value;
        }

        @Override
        public int length() {
            return value.length();
        }

        @Override
        public char charAt(int index) {
            return value.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return value.subSequence(start, end);
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
