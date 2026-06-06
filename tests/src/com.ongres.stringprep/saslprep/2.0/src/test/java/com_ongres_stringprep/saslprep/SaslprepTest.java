/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ongres_stringprep.saslprep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ongres.saslprep.SASLprep;
import org.junit.jupiter.api.Test;

public class SaslprepTest {
    private static final SASLprep SASL_PREP = new SASLprep();

    @Test
    void preparesRfc4013Examples() {
        assertThat(prepare("I\u00ADX", true)).isEqualTo("IX");
        assertThat(prepare("user", true)).isEqualTo("user");
        assertThat(prepare("USER", true)).isEqualTo("USER");
        assertThat(prepare("\u00AA", true)).isEqualTo("a");
        assertThat(prepare("\u2168", true)).isEqualTo("IX");
        assertThat(prepare("\uD840\uDC00", true)).isEqualTo("\uD840\uDC00");
    }

    @Test
    void removesCharactersCommonlyMappedToNothingBeforeValidation() {
        String prepared = prepare("pass\u00AD\u034Fword\u200B\uFE00", true);

        assertThat(prepared).isEqualTo("password");
    }

    @Test
    void appliesNfkcCompatibilityNormalizationWithoutCaseFolding() {
        String fullwidthUserWithDigits = "\uFF35\uFF53\uFF45\uFF52\uFF11\uFF12\uFF13";
        String ligatureAndRomanNumeral = "\uFB01-\u2168";

        assertThat(prepare(fullwidthUserWithDigits, true)).isEqualTo("User123");
        assertThat(prepare(ligatureAndRomanNumeral, true)).isEqualTo("fi-IX");
    }

    @Test
    void mapsNonAsciiSpaceCharactersToAsciiSpaceDuringPreparation() {
        assertThat(prepare("first\u00A0second", true)).isEqualTo("first second");
        assertThat(prepare("first\u2007second", true)).isEqualTo("first second");
        assertThat(prepare("first\u202Fsecond", true)).isEqualTo("first second");
    }

    @Test
    void preservesAsciiSpacesWithoutTrimmingOrCollapsing() {
        assertThat(prepare(" leading  inner  trailing ", true))
                .isEqualTo(" leading  inner  trailing ");
    }

    @Test
    void handlesSupplementaryCodePointsWithoutSplittingSurrogatePairs() {
        String cjkExtensionB = "\uD840\uDC00";
        String deseretCapitalLetterLongI = "\uD801\uDC00";

        assertThat(prepare("a" + cjkExtensionB + "\u00AD" + deseretCapitalLetterLongI + "b", true))
                .isEqualTo("a" + cjkExtensionB + deseretCapitalLetterLongI + "b");
    }

    @Test
    void rejectsProhibitedCharactersFromEachSaslprepCategory() {
        assertProhibited("ASCII control", "ok\u0007");
        assertProhibited("non-ASCII control", "ok\u0080");
        assertProhibited("private use", "ok\uE000");
        assertProhibited("non-character code point", "ok\uFDD0");
        assertProhibited("surrogate code point", "ok\uD800");
        assertProhibited("plain text inappropriate", "ok\uFFF9");
        assertProhibited("canonical representation inappropriate", "ok\u2FF0");
        assertProhibited("display property changing", "ok\u200E");
        assertProhibited("tagging", "ok\uDB40\uDC01");
    }

    @Test
    void rejectsUnassignedCodePointsOnlyForStoredStrings() {
        String stringWithUnassignedCodePoint = "user\u0221";

        assertThat(prepare(stringWithUnassignedCodePoint, false)).isEqualTo(stringWithUnassignedCodePoint);
        assertThatThrownBy(() -> prepare(stringWithUnassignedCodePoint, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unassigned code point")
                .hasMessageContaining("0x0221");
    }

    @Test
    void enforcesBidirectionalRuleForRandAlCatCharacters() {
        assertThat(prepare("\u0627\u0661\u0628", true)).isEqualTo("\u0627\u0661\u0628");
        assertThat(prepare("\u05D0\u05D1", true)).isEqualTo("\u05D0\u05D1");

        assertThatThrownBy(() -> prepare("\u0627A\u0628", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RandALCat and LCat");

        assertThatThrownBy(() -> prepare("\u06271", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RandALCat character is not the first and the last character");

        assertThatThrownBy(() -> prepare("1\u0627", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RandALCat character is not the first and the last character");
    }

    @Test
    void appliesBidirectionalRuleAfterCompatibilityNormalization() {
        assertThat(prepare("\u0627\uFF11\u0628", true)).isEqualTo("\u06271\u0628");

        assertThatThrownBy(() -> prepare("\u0627\uFF21\u0628", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RandALCat and LCat");
    }

    @Test
    void rejectsNullInput() {
        assertThatNullPointerException().isThrownBy(() -> prepare(null, true));
    }

    private static void assertProhibited(String description, String value) {
        assertThatThrownBy(() -> prepare(value, true))
                .as(description)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prohibited");
    }

    private static String prepare(String value, boolean stored) {
        if (stored) {
            return SASL_PREP.prepareStored(value);
        }
        return SASL_PREP.prepareQuery(value);
    }
}
