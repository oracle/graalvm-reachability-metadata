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

import com.ongres.saslprep.SaslPrep;
import org.junit.jupiter.api.Test;

public class SaslprepTest {
    @Test
    void preparesRfc4013Examples() {
        assertThat(SaslPrep.saslPrep("I\u00ADX", true)).isEqualTo("IX");
        assertThat(SaslPrep.saslPrep("user", true)).isEqualTo("user");
        assertThat(SaslPrep.saslPrep("USER", true)).isEqualTo("USER");
        assertThat(SaslPrep.saslPrep("\u00AA", true)).isEqualTo("a");
        assertThat(SaslPrep.saslPrep("\u2168", true)).isEqualTo("IX");
        assertThat(SaslPrep.saslPrep("\uD840\uDC00", true)).isEqualTo("\uD840\uDC00");
    }

    @Test
    void removesCharactersCommonlyMappedToNothingBeforeValidation() {
        String prepared = SaslPrep.saslPrep("pass\u00AD\u034Fword\u200B\uFE00", true);

        assertThat(prepared).isEqualTo("password");
    }

    @Test
    void appliesNfkcCompatibilityNormalizationWithoutCaseFolding() {
        String fullwidthUserWithDigits = "\uFF35\uFF53\uFF45\uFF52\uFF11\uFF12\uFF13";
        String ligatureAndRomanNumeral = "\uFB01-\u2168";

        assertThat(SaslPrep.saslPrep(fullwidthUserWithDigits, true)).isEqualTo("User123");
        assertThat(SaslPrep.saslPrep(ligatureAndRomanNumeral, true)).isEqualTo("fi-IX");
    }

    @Test
    void mapsNonAsciiSpaceCharactersToAsciiSpaceDuringPreparation() {
        assertThat(SaslPrep.saslPrep("first\u00A0second", true)).isEqualTo("first second");
        assertThat(SaslPrep.saslPrep("first\u2007second", true)).isEqualTo("first second");
        assertThat(SaslPrep.saslPrep("first\u202Fsecond", true)).isEqualTo("first second");
    }

    @Test
    void preservesAsciiSpacesWithoutTrimmingOrCollapsing() {
        assertThat(SaslPrep.saslPrep(" leading  inner  trailing ", true))
                .isEqualTo(" leading  inner  trailing ");
    }

    @Test
    void handlesSupplementaryCodePointsWithoutSplittingSurrogatePairs() {
        String cjkExtensionB = "\uD840\uDC00";
        String deseretCapitalLetterLongI = "\uD801\uDC00";

        assertThat(SaslPrep.saslPrep("a" + cjkExtensionB + "\u00AD" + deseretCapitalLetterLongI + "b", true))
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

        assertThat(SaslPrep.saslPrep(stringWithUnassignedCodePoint, false)).isEqualTo(stringWithUnassignedCodePoint);
        assertThatThrownBy(() -> SaslPrep.saslPrep(stringWithUnassignedCodePoint, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prohibited character")
                .hasMessageContaining("\u0221");
    }

    @Test
    void enforcesBidirectionalRuleForRandAlCatCharacters() {
        assertThat(SaslPrep.saslPrep("\u0627\u0661\u0628", true)).isEqualTo("\u0627\u0661\u0628");
        assertThat(SaslPrep.saslPrep("\u05D0\u05D1", true)).isEqualTo("\u05D0\u05D1");

        assertThatThrownBy(() -> SaslPrep.saslPrep("\u0627A\u0628", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RandALCat and LCat");

        assertThatThrownBy(() -> SaslPrep.saslPrep("\u06271", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RandALCat character is not the first and the last characters");

        assertThatThrownBy(() -> SaslPrep.saslPrep("1\u0627", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RandALCat character is not the first and the last characters");
    }

    @Test
    void appliesBidirectionalRuleAfterCompatibilityNormalization() {
        assertThat(SaslPrep.saslPrep("\u0627\uFF11\u0628", true)).isEqualTo("\u06271\u0628");

        assertThatThrownBy(() -> SaslPrep.saslPrep("\u0627\uFF21\u0628", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RandALCat and LCat");
    }

    @Test
    void rejectsNullInput() {
        assertThatNullPointerException().isThrownBy(() -> SaslPrep.saslPrep(null, true));
    }

    private static void assertProhibited(String description, String value) {
        assertThatThrownBy(() -> SaslPrep.saslPrep(value, true))
                .as(description)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prohibited character");
    }
}
