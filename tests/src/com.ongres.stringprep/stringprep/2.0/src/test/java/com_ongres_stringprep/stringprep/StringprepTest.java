/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ongres_stringprep.stringprep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ongres.stringprep.Option;
import com.ongres.stringprep.Profile;
import com.ongres.stringprep.Tables;
import java.text.Normalizer;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

public class StringprepTest {
    @Test
    void identifiesUnassignedCodePointRanges() {
        assertAccepted(Tables::unassignedCodePoints,
                0x0221,
                0x0234,
                0x024F,
                0x0370,
                0x0373,
                0xE0002,
                0xE0080,
                0xEFFFD);

        assertRejected(Tables::unassignedCodePoints,
                0x0020,
                0x0220,
                0x0222,
                0x0233,
                0x0250,
                0xD7A3,
                0xE000,
                0xF0000,
                0x10FFFF);
    }

    @Test
    void mapsTableB1CharactersToNothing() {
        assertAccepted(Tables::mapToNothing,
                0x00AD,
                0x034F,
                0x1806,
                0x180B,
                0x180C,
                0x180D,
                0x200B,
                0x200C,
                0x200D,
                0x2060,
                0xFE00,
                0xFE0F,
                0xFEFF);

        assertRejected(Tables::mapToNothing,
                'a',
                0x00AE,
                0x180E,
                0x200E,
                0x2061,
                0xFE10);
    }

    @Test
    void mapsCaseAndCompatibilityCharactersForNfkc() {
        assertThat(Tables.mapWithNfkc('A')).containsExactly('a');
        assertThat(Tables.mapWithNfkc(0x00DF)).containsExactly('s', 's');
        assertThat(Tables.mapWithNfkc(0x0130)).containsExactly('i', 0x0307);
        assertThat(Tables.mapWithNfkc(0x0149)).containsExactly(0x02BC, 'n');
        assertThat(Tables.mapWithNfkc(0x0390)).containsExactly(0x03B9, 0x0308, 0x0301);
        assertThat(Tables.mapWithNfkc(0x20A8)).containsExactly('r', 's');
        assertThat(Tables.mapWithNfkc(0x212A)).containsExactly('k');
        assertThat(Tables.mapWithNfkc(0xFB00)).containsExactly('f', 'f');
        assertThat(Tables.mapWithNfkc('z')).containsExactly('z');
    }

    @Test
    void mapsCaseCharactersWithoutNormalizingCompatibilityForms() {
        assertThat(Tables.mapWithoutNormalization('A')).containsExactly('a');
        assertThat(Tables.mapWithoutNormalization(0x00DF)).containsExactly('s', 's');
        assertThat(Tables.mapWithoutNormalization(0x0130)).containsExactly('i', 0x0307);
        assertThat(Tables.mapWithoutNormalization(0x0149)).containsExactly(0x02BC, 'n');
        assertThat(Tables.mapWithoutNormalization(0x0390)).containsExactly(0x03B9, 0x0308, 0x0301);
        assertThat(Tables.mapWithoutNormalization(0x212A)).containsExactly('k');

        assertThat(Tables.mapWithoutNormalization(0x20A8)).containsExactly(0x20A8);
        assertThat(Tables.mapWithoutNormalization(0x2100)).containsExactly(0x2100);
        assertThat(Tables.mapWithoutNormalization('z')).containsExactly('z');
    }

    @Test
    void mapsSupplementaryPlaneCodePoints() {
        assertThat(Tables.mapWithoutNormalization(0x10425)).containsExactly(0x1044D);
        assertThat(Tables.mapWithoutNormalization(0x10426)).containsExactly(0x10426);
        assertThat(Tables.mapWithNfkc(0x1D7BB)).containsExactly(0x03C3);
        assertThat(Tables.mapWithNfkc(0x1D7BC)).containsExactly(0x1D7BC);
    }

    @Test
    void composesMappingTablesForACompleteNfkcProfileMappingStep() {
        String fullwidthHello = "\uFF28\uFF45\uFF4C\uFF4C\uFF4F";
        String softHyphen = "\u00AD";
        String fullwidthDigits = "\uFF11\uFF12\uFF13";

        String mapped = mapForNfkcProfile(fullwidthHello + softHyphen + fullwidthDigits);

        assertThat(Normalizer.normalize(mapped, Normalizer.Form.NFKC))
                .isEqualTo("hello123");
    }

    @Test
    void detectsAllProhibitedCharacterCategories() {
        assertAccepted(Tables::prohibitionAsciiSpace, 0x0020);
        assertRejected(Tables::prohibitionAsciiSpace, 0x0009, 0x00A0, 'A');

        assertAccepted(Tables::prohibitionNonAsciiSpace,
                0x00A0, 0x1680, 0x2000, 0x200B, 0x202F, 0x205F, 0x3000);
        assertRejected(Tables::prohibitionNonAsciiSpace, 0x0020, 0x200C, 0x3001);

        assertAccepted(Tables::prohibitionAsciiControl, 0x0000, 0x001F, 0x007F);
        assertRejected(Tables::prohibitionAsciiControl, 0x0020, 0x0080);

        assertAccepted(Tables::prohibitionNonAsciiControl, 0x0080, 0x009F, 0x06DD, 0x070F, 0x180E,
                0x200C, 0x200D, 0x2028, 0x2029, 0x206A, 0x206F, 0xFEFF, 0xFFF9, 0xFFFC, 0x1D173, 0x1D17A);
        assertRejected(Tables::prohibitionNonAsciiControl, 0x007F, 0x00A0, 0x2069, 0x2070, 0xFFFD);

        assertAccepted(Tables::prohibitionPrivateUse, 0xE000, 0xF8FF, 0xF0000, 0xFFFFD, 0x100000, 0x10FFFD);
        assertRejected(Tables::prohibitionPrivateUse, 0xDFFF, 0xF900, 0xEFFFF, 0xFFFFE, 0x10FFFF);

        assertAccepted(Tables::prohibitionNonCharacterCodePoints, 0xFDD0, 0xFDEF, 0xFFFE, 0xFFFF,
                0x1FFFE, 0x1FFFF, 0x10FFFE, 0x10FFFF);
        assertRejected(Tables::prohibitionNonCharacterCodePoints, 0xFDCF, 0xFDF0, 0xFFFD, 0x10000, 0x10FFFD);

        assertAccepted(Tables::prohibitionSurrogateCodes, 0xD800, 0xDFFF);
        assertRejected(Tables::prohibitionSurrogateCodes, 0xD7FF, 0xE000);

        assertAccepted(Tables::prohibitionInappropriatePlainText, 0xFFF9, 0xFFFA, 0xFFFB, 0xFFFC, 0xFFFD);
        assertRejected(Tables::prohibitionInappropriatePlainText, 0xFFF8, 0xFFFE);

        assertAccepted(Tables::prohibitionInappropriateCanonicalRepresentation, 0x2FF0, 0x2FFB);
        assertRejected(Tables::prohibitionInappropriateCanonicalRepresentation, 0x2FEF, 0x2FFC);

        assertAccepted(Tables::prohibitionChangeDisplayProperties, 0x0340, 0x0341, 0x200E, 0x200F,
                0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x206A, 0x206F);
        assertRejected(Tables::prohibitionChangeDisplayProperties, 0x033F, 0x0342, 0x200D, 0x2010, 0x2070);

        assertAccepted(Tables::prohibitionTaggingCharacters, 0xE0001, 0xE0020, 0xE007F);
        assertRejected(Tables::prohibitionTaggingCharacters, 0xE0000, 0xE001F, 0xE0080);
    }

    @Test
    void detectsBidirectionalCategories() {
        assertAccepted(Tables::bidirectionalPropertyRorAL,
                0x05D0,
                0x05EA,
                0x0627,
                0x06DD,
                0x200F,
                0xFB1D,
                0xFEFC);
        assertRejected(Tables::bidirectionalPropertyRorAL,
                'A',
                'a',
                0x05EF,
                0x200E,
                0xFEFD);

        assertAccepted(Tables::bidirectionalPropertyL,
                'A',
                'z',
                0x00AA,
                0x03B1,
                0x200E,
                0x212A,
                0xFB00,
                0x1D400);
        assertRejected(Tables::bidirectionalPropertyL,
                0x0020,
                0x05D0,
                0x200F,
                0x202E,
                0xE0001);
    }

    @Test
    void enforcesBidirectionalRule() {
        Profile bidiProfile = () -> Set.of(Option.CHECK_BIDI);

        assertThat(bidiProfile.prepareQuery(codePoints(0x0061, 0x0062, 0x0063))).isEqualTo("abc");
        assertThat(bidiProfile.prepareQuery(codePoints(0x05D0, 0x05D1)))
                .isEqualTo("\u05D0\u05D1");
        assertThat(bidiProfile.prepareQuery(codePoints(0x05D0, 0x0030, 0x05D1)))
                .isEqualTo("\u05D00\u05D1");
        assertThat(bidiProfile.prepareQuery(""))
                .isEmpty();

        assertThatThrownBy(() -> bidiProfile.prepareQuery(codePoints(0x05D0, 0x0061, 0x05D1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RandALCat and LCat");

        assertThatThrownBy(() -> bidiProfile.prepareQuery(codePoints(0x05D0, 0x0030)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not the first and the last");

        assertThatThrownBy(() -> bidiProfile.prepareQuery(codePoints(0x0030, 0x05D0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not the first and the last");

        assertThatThrownBy(() -> bidiProfile.prepareQuery(codePoints(0x0061, 0x202E, 0x0062)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prohibited control character")
                .hasMessageContaining("0x202E");
    }

    private static String mapForNfkcProfile(String value) {
        return mapProfile(value, Tables::mapWithNfkc);
    }

    private static String mapProfile(String value, IntFunction<int[]> mapping) {
        StringBuilder mapped = new StringBuilder();
        value.codePoints()
                .filter(codePoint -> !Tables.mapToNothing(codePoint))
                .forEach(codePoint -> appendCodePoints(mapped, mapping.apply(codePoint)));
        return mapped.toString();
    }

    private static void appendCodePoints(StringBuilder builder, int[] codePoints) {
        for (int codePoint : codePoints) {
            builder.appendCodePoint(codePoint);
        }
    }

    private static String codePoints(int... codePoints) {
        StringBuilder builder = new StringBuilder();
        appendCodePoints(builder, codePoints);
        return builder.toString();
    }

    private static void assertAccepted(IntPredicate predicate, int... codePoints) {
        for (int codePoint : codePoints) {
            assertThat(predicate.test(codePoint))
                    .as("U+%04X should be accepted by predicate", codePoint)
                    .isTrue();
        }
    }

    private static void assertRejected(IntPredicate predicate, int... codePoints) {
        for (int codePoint : codePoints) {
            assertThat(predicate.test(codePoint))
                    .as("U+%04X should be rejected by predicate", codePoint)
                    .isFalse();
        }
    }
}
