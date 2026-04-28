/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.SparseBitSet;

import com.zaxxer.sparsebits.SparseBitSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SparseBitSetTest {
    @Test
    void singleBitRangeAndBooleanMutationsMatchBitSetSemantics() {
        SparseBitSet sparse = new SparseBitSet();
        BitSet expected = new BitSet();

        for (int index : new int[]{0, 1, 63, 64, 65, 2048, 4095, 65_536, 1_000_000}) {
            sparse.set(index);
            expected.set(index);
        }
        sparse.set(10, 20);
        expected.set(10, 20);
        sparse.clear(12);
        expected.clear(12);
        sparse.clear(18, 21);
        expected.clear(18, 21);
        sparse.set(5000, false);
        expected.set(5000, false);
        sparse.set(5001, true);
        expected.set(5001, true);
        sparse.flip(0);
        expected.flip(0);
        sparse.flip(15, 25);
        expected.flip(15, 25);

        sparse.and(1, true);
        sparse.and(5001, false);
        sparse.or(5002, true);
        sparse.or(5003, false);
        sparse.xor(64, true);
        sparse.xor(5002, true);
        sparse.andNot(65_536, true);
        sparse.andNot(5004, false);
        expected.set(5001, false);
        expected.set(5002, false);
        expected.flip(64);
        expected.clear(65_536);

        assertMatches(sparse, expected);
        assertNavigationMatches(sparse, expected, 0, 1, 12, 18, 63, 64, 5000, 5001, 65_536, 999_999, 1_000_000);
    }

    @Test
    void booleanRangeAssignmentsSetAndClearSelectedSpans() {
        SparseBitSet sparse = sparseOf(2, 5, 64, 130, 4096, 90_000);
        BitSet expected = bitSetOf(2, 5, 64, 130, 4096, 90_000);

        sparse.set(10, 18, true);
        expected.set(10, 18, true);
        sparse.set(64, 131, false);
        expected.set(64, 131, false);
        sparse.set(2048, 2052, true);
        expected.set(2048, 2052, true);
        sparse.set(2049, 2051, false);
        expected.set(2049, 2051, false);
        sparse.set(90_001, 90_001, true);
        expected.set(90_001, 90_001, true);
        sparse.set(0, 0, false);
        expected.set(0, 0, false);

        assertMatches(sparse, expected);
        assertNavigationMatches(sparse, expected, 0, 2, 10, 17, 18, 64, 130, 2048, 2051, 90_000, 90_001);
    }

    @Test
    void wholeSetLogicalOperationsDoNotMutateOperandsAndMatchBitSet() {
        SparseBitSet left = sparseOf(1, 2, 64, 1000, 4096, 70_000);
        SparseBitSet right = sparseOf(2, 3, 64, 4096, 80_000);
        BitSet leftBits = bitSetOf(1, 2, 64, 1000, 4096, 70_000);
        BitSet rightBits = bitSetOf(2, 3, 64, 4096, 80_000);

        BitSet expectedAnd = (BitSet) leftBits.clone();
        expectedAnd.and(rightBits);
        assertMatches(SparseBitSet.and(left, right), expectedAnd);

        BitSet expectedOr = (BitSet) leftBits.clone();
        expectedOr.or(rightBits);
        assertMatches(SparseBitSet.or(left, right), expectedOr);

        BitSet expectedXor = (BitSet) leftBits.clone();
        expectedXor.xor(rightBits);
        assertMatches(SparseBitSet.xor(left, right), expectedXor);

        BitSet expectedAndNot = (BitSet) leftBits.clone();
        expectedAndNot.andNot(rightBits);
        assertMatches(SparseBitSet.andNot(left, right), expectedAndNot);

        assertMatches(left, leftBits);
        assertMatches(right, rightBits);
    }

    @Test
    void rangedLogicalOperationsOnlyAffectRequestedIndexes() {
        SparseBitSet mask = sparseOf(10, 20, 30, 40);
        BitSet maskBits = bitSetOf(10, 20, 30, 40);

        SparseBitSet andTarget = sparseOf(5, 10, 15, 20, 25);
        BitSet expectedAnd = bitSetOf(5, 10, 15, 20, 25);
        andTarget.and(10, 21, mask);
        applyRange(expectedAnd, maskBits, 10, 21, Operation.AND);
        assertMatches(andTarget, expectedAnd);

        SparseBitSet orTarget = sparseOf(5, 10, 15, 20, 25);
        BitSet expectedOr = bitSetOf(5, 10, 15, 20, 25);
        orTarget.or(10, 31, mask);
        applyRange(expectedOr, maskBits, 10, 31, Operation.OR);
        assertMatches(orTarget, expectedOr);

        SparseBitSet xorTarget = sparseOf(5, 10, 15, 20, 25);
        BitSet expectedXor = bitSetOf(5, 10, 15, 20, 25);
        xorTarget.xor(10, 31, mask);
        applyRange(expectedXor, maskBits, 10, 31, Operation.XOR);
        assertMatches(xorTarget, expectedXor);

        SparseBitSet andNotTarget = sparseOf(5, 10, 15, 20, 25);
        BitSet expectedAndNot = bitSetOf(5, 10, 15, 20, 25);
        andNotTarget.andNot(10, 31, mask);
        applyRange(expectedAndNot, maskBits, 10, 31, Operation.AND_NOT);
        assertMatches(andNotTarget, expectedAndNot);
    }

    @Test
    void rangeViewsCloneEqualityHashCodeAndClearingBehaveConsistently() {
        SparseBitSet original = sparseOf(3, 10, 15, 16, 20, 65_536);
        SparseBitSet slice = original.get(10, 17);
        assertMatches(slice, bitSetOf(10, 15, 16));

        SparseBitSet clone = original.clone();
        assertThat(clone).isEqualTo(original);
        assertThat(clone.hashCode()).isEqualTo(original.hashCode());

        clone.clear(15);
        assertThat(clone).isNotEqualTo(original);
        assertThat(original.get(15)).isTrue();
        assertThat(clone.get(15)).isFalse();

        clone.clear();
        assertThat(clone.isEmpty()).isTrue();
        assertThat(clone.cardinality()).isZero();
        assertThat(clone.length()).isZero();
        assertThat(clone.nextSetBit(0)).isEqualTo(-1);
        assertThat(clone.nextClearBit(0)).isZero();
    }

    @Test
    void navigationIntersectionsTextAndSizingExposeUsefulState() {
        SparseBitSet sparse = sparseOf(2, 4, 5, 6, 2000);

        assertThat(sparse.intersects(sparseOf(1, 3, 2000))).isTrue();
        assertThat(sparse.intersects(sparseOf(1, 3, 1999))).isFalse();
        assertThat(sparse.intersects(4, 7, sparseOf(5, 8))).isTrue();
        assertThat(sparse.intersects(7, 10, sparseOf(5, 8))).isFalse();

        assertNavigationMatches(sparse, bitSetOf(2, 4, 5, 6, 2000), 0, 2, 3, 6, 7, 1999, 2000, 2001);

        assertThat(sparse.size()).isGreaterThanOrEqualTo(sparse.cardinality());
        assertThat(sparse.toString()).contains("2").contains("2000");
        assertThat(sparse.statistics()).contains("Size").contains("Length");
        String[] statisticsValues = new String[16];
        assertThat(sparse.statistics(statisticsValues)).contains("Cardinality");
        assertThat(statisticsValues).contains(String.valueOf(sparse.size()), String.valueOf(sparse.length()), String.valueOf(sparse.cardinality()));
        sparse.toStringCompaction(true);
        assertThat(sparse.toString()).isNotBlank();
        sparse.toStringCompaction(2);
        assertThat(sparse.toString()).isNotBlank();
        sparse.toStringCompaction(false);
        assertThat(sparse.toString()).isNotBlank();
    }

    @Test
    void toStringCompactionControlsRunLengthFormatting() {
        SparseBitSet sparse = new SparseBitSet();
        sparse.set(10, 15);
        sparse.set(20);

        sparse.toStringCompaction(0);
        assertThat(sparse).hasToString("{10, 11, 12, 13, 14, 20}");

        sparse.toStringCompaction(2);
        assertThat(sparse).hasToString("{10..14, 20}");

        sparse.toStringCompaction(5);
        assertThat(sparse).hasToString("{10, 11, 12, 13, 14, 20}");
    }

    @Test
    void invalidIndexesAndRangesAreRejected() {
        assertThatThrownBy(() -> new SparseBitSet(-1)).isInstanceOf(NegativeArraySizeException.class);
        assertThatThrownBy(() -> new SparseBitSet().set(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> new SparseBitSet().get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> new SparseBitSet().clear(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> new SparseBitSet().flip(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> new SparseBitSet().nextSetBit(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThat(new SparseBitSet().previousSetBit(-1)).isEqualTo(-1);
        assertThatThrownBy(() -> new SparseBitSet().previousSetBit(-2)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> new SparseBitSet().set(5, 3)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> new SparseBitSet().clear(5, 3)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> new SparseBitSet().flip(5, 3)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> new SparseBitSet().get(5, 3)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> new SparseBitSet().and(5, 3, new SparseBitSet())).isInstanceOf(IndexOutOfBoundsException.class);
    }

    private static SparseBitSet sparseOf(int... indexes) {
        SparseBitSet sparse = new SparseBitSet();
        for (int index : indexes) {
            sparse.set(index);
        }
        return sparse;
    }

    private static BitSet bitSetOf(int... indexes) {
        BitSet bitSet = new BitSet();
        for (int index : indexes) {
            bitSet.set(index);
        }
        return bitSet;
    }

    private static void assertMatches(SparseBitSet actual, BitSet expected) {
        assertThat(setBits(actual)).isEqualTo(setBits(expected));
        assertThat(actual.cardinality()).isEqualTo(expected.cardinality());
        assertThat(actual.length()).isEqualTo(expected.length());
        assertThat(actual.isEmpty()).isEqualTo(expected.isEmpty());
    }

    private static void assertNavigationMatches(SparseBitSet actual, BitSet expected, int... fromIndexes) {
        for (int fromIndex : fromIndexes) {
            assertThat(actual.nextSetBit(fromIndex)).as("next set bit from %s", fromIndex).isEqualTo(expected.nextSetBit(fromIndex));
            assertThat(actual.nextClearBit(fromIndex)).as("next clear bit from %s", fromIndex).isEqualTo(expected.nextClearBit(fromIndex));
            assertThat(actual.previousSetBit(fromIndex)).as("previous set bit from %s", fromIndex).isEqualTo(expected.previousSetBit(fromIndex));
            assertThat(actual.previousClearBit(fromIndex)).as("previous clear bit from %s", fromIndex).isEqualTo(expected.previousClearBit(fromIndex));
        }
    }

    private static List<Integer> setBits(SparseBitSet sparse) {
        List<Integer> bits = new ArrayList<>();
        for (int bit = sparse.nextSetBit(0); bit >= 0; bit = sparse.nextSetBit(bit + 1)) {
            bits.add(bit);
        }
        return bits;
    }

    private static List<Integer> setBits(BitSet bitSet) {
        List<Integer> bits = new ArrayList<>();
        for (int bit = bitSet.nextSetBit(0); bit >= 0; bit = bitSet.nextSetBit(bit + 1)) {
            bits.add(bit);
        }
        return bits;
    }

    private static void applyRange(BitSet target, BitSet mask, int fromIndex, int toIndex, Operation operation) {
        for (int index = fromIndex; index < toIndex; index++) {
            boolean targetValue = target.get(index);
            boolean maskValue = mask.get(index);
            if (operation == Operation.AND) {
                target.set(index, targetValue && maskValue);
            } else if (operation == Operation.OR) {
                target.set(index, targetValue || maskValue);
            } else if (operation == Operation.XOR) {
                target.set(index, targetValue ^ maskValue);
            } else {
                target.set(index, targetValue && !maskValue);
            }
        }
    }

    private enum Operation {
        AND,
        OR,
        XOR,
        AND_NOT
    }
}
