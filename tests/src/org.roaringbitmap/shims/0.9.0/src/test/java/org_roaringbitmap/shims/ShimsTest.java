/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_roaringbitmap.shims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.roaringbitmap.ArraysShim;

public class ShimsTest {
    @Test
    void charRangeEqualsComparesOnlyRequestedWindows() {
        char[] first = {'x', 'r', 'o', 'a', 'r', 'y'};
        char[] second = {'z', 'z', 'r', 'o', 'a', 'r', 'q'};

        assertThat(ArraysShim.equals(first, 1, 5, second, 2, 6)).isTrue();
    }

    @Test
    void charRangeEqualsRejectsDifferentContentWithinSameLengthWindow() {
        char[] first = {'r', 'o', 'a', 'r'};
        char[] second = {'r', 'o', 'u', 'r'};

        assertThat(ArraysShim.equals(first, 0, first.length, second, 0, second.length)).isFalse();
    }

    @Test
    void charRangeEqualsRejectsDifferentWindowLengths() {
        char[] first = {'a', 'b', 'c', 'd'};
        char[] second = {'a', 'b', 'c', 'd'};

        assertThat(ArraysShim.equals(first, 0, 3, second, 0, 4)).isFalse();
    }

    @Test
    void charRangeEqualsAcceptsEmptyRangesAtDifferentPositions() {
        char[] first = {'a', 'b'};
        char[] second = {'x', 'y', 'z'};

        assertThat(ArraysShim.equals(first, 1, 1, second, 3, 3)).isTrue();
    }

    @Test
    void charRangeEqualsRejectsInvalidRangeBoundaries() {
        char[] values = {'r', 'o', 'a', 'r'};

        assertThatThrownBy(() -> ArraysShim.equals(values, 3, 1, values, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArraysShim.equals(values, -1, 1, values, 0, 1))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
        assertThatThrownBy(() -> ArraysShim.equals(values, 0, 1, values, 0, values.length + 1))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
    }

    @Test
    void byteRangeMismatchReturnsMinusOneForEqualWindows() {
        byte[] first = {9, 10, 11, 12, 13};
        byte[] second = {0, 0, 10, 11, 12, 99};

        assertThat(ArraysShim.mismatch(first, 1, 4, second, 2, 5)).isEqualTo(-1);
    }

    @Test
    void byteRangeMismatchReturnsRelativeOffsetOfFirstDifferentByte() {
        byte[] first = {1, 2, 3, 4, 5};
        byte[] second = {7, 2, 3, 9, 5};

        assertThat(ArraysShim.mismatch(first, 1, 5, second, 1, 5)).isEqualTo(2);
    }

    @Test
    void byteRangeMismatchReturnsShorterLengthWhenOneWindowIsPrefixOfAnother() {
        byte[] first = {4, 5, 6, 7};
        byte[] second = {0, 5, 6, 7, 8};

        assertThat(ArraysShim.mismatch(first, 1, 4, second, 1, 5)).isEqualTo(3);
    }

    @Test
    void byteRangeMismatchHandlesEmptyRanges() {
        byte[] first = {1, 2, 3};
        byte[] second = {4, 5};

        assertThat(ArraysShim.mismatch(first, 2, 2, second, 1, 1)).isEqualTo(-1);
        assertThat(ArraysShim.mismatch(first, 2, 2, second, 0, 1)).isZero();
    }

    @Test
    void byteRangeMismatchRejectsInvalidRangeBoundaries() {
        byte[] values = {1, 2, 3, 4};

        assertThatThrownBy(() -> ArraysShim.mismatch(values, 3, 1, values, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArraysShim.mismatch(values, -1, 1, values, 0, 1))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
        assertThatThrownBy(() -> ArraysShim.mismatch(values, 0, 1, values, 0, values.length + 1))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
    }

    @Test
    void charRangeEqualsRejectsNullArrays() {
        char[] values = {'r', 'o', 'a', 'r'};

        assertThatThrownBy(() -> ArraysShim.equals(null, 0, 1, values, 0, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ArraysShim.equals(values, 0, 1, null, 0, 1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void byteRangeMismatchRejectsNullArrays() {
        byte[] values = {1, 2, 3, 4};

        assertThatThrownBy(() -> ArraysShim.mismatch(null, 0, 1, values, 0, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ArraysShim.mismatch(values, 0, 1, null, 0, 1))
                .isInstanceOf(NullPointerException.class);
    }
}
