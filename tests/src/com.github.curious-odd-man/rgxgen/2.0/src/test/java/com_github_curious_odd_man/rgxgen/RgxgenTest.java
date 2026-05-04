/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_curious_odd_man.rgxgen;

import com.github.curiousoddman.rgxgen.RgxGen;
import com.github.curiousoddman.rgxgen.config.RgxGenOption;
import com.github.curiousoddman.rgxgen.config.RgxGenProperties;
import com.github.curiousoddman.rgxgen.iterators.StringIterator;
import com.github.curiousoddman.rgxgen.model.RgxGenCharsDefinition;
import com.github.curiousoddman.rgxgen.model.SymbolRange;
import com.github.curiousoddman.rgxgen.model.UnicodeCategory;
import com.github.curiousoddman.rgxgen.model.WhitespaceChar;
import com.github.curiousoddman.rgxgen.parsing.dflt.RgxGenParseException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RgxgenTest {
    private static final int SAMPLE_COUNT = 30;

    @Test
    void generatedValuesMatchCommonSupportedRegexConstructs() {
        assertGeneratedValuesMatch("[A-Z]{2}\\d{3}", 0);
        assertGeneratedValuesMatch("(cat|dog)-(red|blue)?", 0);
        assertGeneratedValuesMatch("\\Q.*literal?\\E[0-2]", 0);
        assertGeneratedValuesMatch("\\x41\\u0042[\\x{43}-\\x{44}]", "AB[CD]", 0);
        assertGeneratedValuesMatch("\\w\\W\\d\\D\\s\\S", 0);
        assertGeneratedValuesMatch("([a-c]{2})-\\1", 0);
    }

    @Test
    void nonCapturingGroupsAreNotBackReferenceTargets() {
        RgxGen rgxGen = RgxGen.parse("(?:cat|dog)-([0-2])\\1");

        assertGeneratedValuesMatch(rgxGen, Pattern.compile("(?:cat|dog)-([0-2])\\1"));
    }

    @Test
    void streamUsesTheGeneratorAndProducesMatchingValues() {
        Pattern expected = Pattern.compile("[a-c]{2}[0-2]");
        List<String> generated = RgxGen.parse("[a-c]{2}[0-2]")
                .stream()
                .limit(10)
                .collect(Collectors.toList());

        assertThat(generated)
                .hasSize(10)
                .allSatisfy(value -> assertThat(expected.matcher(value).matches())
                        .as("Generated value %s should match", value)
                        .isTrue());
    }

    @Test
    void uniqueEstimationAndIteratorCoverFiniteLanguage() {
        RgxGen rgxGen = RgxGen.parse("(ab|cd)[0-1]?");

        assertThat(rgxGen.getUniqueEstimation()).contains(BigInteger.valueOf(6));
        StringIterator iterator = rgxGen.iterateUnique();
        List<String> values = drain(iterator);

        assertThat(values).containsExactlyInAnyOrder("ab", "ab0", "ab1", "cd", "cd0", "cd1");
        assertThat(iterator.hasNext()).isFalse();

        iterator.reset();
        String firstAfterReset = iterator.next();
        assertThat(firstAfterReset).isIn(values);
        assertThat(iterator.current()).isEqualTo(firstAfterReset);
    }

    @Test
    void optionsCustomizeDotWhitespaceCaseSensitivityAndInfiniteRepetition() {
        RgxGenProperties properties = new RgxGenProperties();
        RgxGenOption.DOT_MATCHES_ONLY.setInProperties(properties, RgxGenCharsDefinition.of("xy"));
        RgxGenOption.WHITESPACE_DEFINITION.setInProperties(
                properties,
                Arrays.asList(WhitespaceChar.FORM_FEED, WhitespaceChar.VERTICAL_TAB));
        RgxGenOption.CASE_INSENSITIVE.setInProperties(properties, true);
        RgxGenOption.INFINITE_PATTERN_REPETITION.setInProperties(properties, 2);

        assertThat(RgxGenOption.DOT_MATCHES_ONLY.getFromProperties(properties).isAsciiOnly()).isTrue();
        assertThat(RgxGenOption.WHITESPACE_DEFINITION.getFromProperties(properties))
                .containsExactly(WhitespaceChar.FORM_FEED, WhitespaceChar.VERTICAL_TAB);
        assertThat(RgxGenOption.CASE_INSENSITIVE.getFromProperties(properties)).isTrue();
        assertThat(RgxGenOption.INFINITE_PATTERN_REPETITION.getFromProperties(properties)).isEqualTo(2);

        RgxGen dotGenerator = RgxGen.parse(properties, ".");
        assertThat(dotGenerator.getUniqueEstimation()).contains(BigInteger.valueOf(4));
        assertGeneratedValuesMatch(dotGenerator, Pattern.compile("[xyXY]"));

        RgxGen whitespaceGenerator = RgxGen.parse(properties, "\\s");
        assertGeneratedValuesMatch(whitespaceGenerator, Pattern.compile("[\u000B\f]"));

        RgxGen caseInsensitiveGenerator = RgxGen.parse(properties, "ab[cd]");
        assertGeneratedValuesMatch(caseInsensitiveGenerator, Pattern.compile("ab[cd]", Pattern.CASE_INSENSITIVE));

        RgxGen repeatingGenerator = RgxGen.parse(properties, "1*");
        assertGeneratedValuesMatch(repeatingGenerator, Pattern.compile("1{0,2}"));
    }

    @Test
    void notMatchingGenerationProducesCounterExamples() {
        assertGeneratedValuesDoNotMatch("ab[0-2]", 0);
        assertGeneratedValuesDoNotMatch("[a-c]{2}", 0);
        assertGeneratedValuesDoNotMatch("(foo|bar)", 0);
    }

    @Test
    void unicodePropertyEscapesAreParsedAndGenerated() {
        assertGeneratedValuesMatch("\\p{Lu}{2}\\P{Nd}", 0);
    }

    @Test
    void unicodeAndCharacterDefinitionsIntegrateWithGeneration() {
        SymbolRange range = SymbolRange.range('a', 'c');
        assertThat(range.size()).isEqualTo(3);
        assertThat(range.contains('b')).isTrue();
        assertThat(range.contains('d')).isFalse();
        assertThat(range.chars().contains('a')).isTrue();
        assertThat(range.chars().contains('b')).isTrue();
        assertThat(range.chars().contains('c')).isTrue();

        RgxGenCharsDefinition definition = RgxGenCharsDefinition.of(range).withCharacters('x');
        assertThat(definition.isAsciiOnly()).isTrue();
        assertThat(definition.getRangeList()).containsExactly(range);
        assertThat(definition.getCharacters().contains('x')).isTrue();

        RgxGenProperties properties = new RgxGenProperties();
        RgxGenOption.DOT_MATCHES_ONLY.setInProperties(properties, definition);
        assertGeneratedValuesMatch(RgxGen.parse(properties, "."), Pattern.compile("[a-cx]"));

        assertThat(UnicodeCategory.DECIMAL_DIGIT_NUMBER.contains('5')).isTrue();
        assertThat(UnicodeCategory.DECIMAL_DIGIT_NUMBER.contains('a')).isFalse();
        assertThat(UnicodeCategory.ALL_CATEGORIES).containsKey("Nd");

        RgxGenProperties unicodeProperties = new RgxGenProperties();
        RgxGenOption.DOT_MATCHES_ONLY.setInProperties(
                unicodeProperties,
                RgxGenCharsDefinition.of(UnicodeCategory.IN_CYRILLIC));
        assertGeneratedValuesMatch(RgxGen.parse(unicodeProperties, "."), Pattern.compile("\\p{InCyrillic}"));
    }

    @Test
    void invalidPatternsFailWithParseException() {
        assertThrows(RgxGenParseException.class, () -> RgxGen.parse("+abc"));
        assertThrows(RgxGenParseException.class, () -> RgxGen.parse("[abc"));
        assertThrows(RgxGenParseException.class, () -> RgxGen.parse("a{1,2"));
        assertThrows(RgxGenParseException.class, () -> RgxGen.parse("(?xxx)"));
    }

    private static void assertGeneratedValuesMatch(String rgxPattern, int flags) {
        assertGeneratedValuesMatch(rgxPattern, rgxPattern, flags);
    }

    private static void assertGeneratedValuesMatch(String rgxPattern, String expectedPattern, int flags) {
        assertGeneratedValuesMatch(RgxGen.parse(rgxPattern), Pattern.compile(expectedPattern, flags));
    }

    private static void assertGeneratedValuesMatch(RgxGen rgxGen, Pattern expectedPattern) {
        Random random = new Random(12345L);
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            String generated = rgxGen.generate(random);
            assertThat(expectedPattern.matcher(generated).matches())
                    .as("Generated value %s should match %s", generated, expectedPattern)
                    .isTrue();
        }
    }

    private static void assertGeneratedValuesDoNotMatch(String rgxPattern, int flags) {
        assertGeneratedValuesDoNotMatch(RgxGen.parse(rgxPattern), Pattern.compile(rgxPattern, flags));
    }

    private static void assertGeneratedValuesDoNotMatch(RgxGen rgxGen, Pattern forbiddenPattern) {
        Random random = new Random(67890L);
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            String generated = rgxGen.generateNotMatching(random);
            assertThat(forbiddenPattern.matcher(generated).matches())
                    .as("Generated value %s should not match %s", generated, forbiddenPattern)
                    .isFalse();
        }
    }

    private static List<String> drain(StringIterator iterator) {
        Set<String> values = new LinkedHashSet<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return new ArrayList<>(values);
    }
}
