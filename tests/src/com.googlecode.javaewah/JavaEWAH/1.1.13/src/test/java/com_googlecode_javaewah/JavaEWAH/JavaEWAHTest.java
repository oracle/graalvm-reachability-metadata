/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_googlecode_javaewah.JavaEWAH;

import com.googlecode.javaewah.ChunkIterator;
import com.googlecode.javaewah.EWAHCompressedBitmap;
import com.googlecode.javaewah.FastAggregation;
import com.googlecode.javaewah.IntIterator;
import com.googlecode.javaewah.datastructure.BitSet;
import com.googlecode.javaewah32.EWAHCompressedBitmap32;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaEWAHTest {
    @Test
    void ewahCompressedBitmapSupportsBitQueriesAndIteration() {
        EWAHCompressedBitmap bitmap = EWAHCompressedBitmap.bitmapOf(0, 1, 2, 63, 64, 130);

        assertThat(bitmap.isEmpty()).isFalse();
        assertThat(bitmap.cardinality()).isEqualTo(6);
        assertThat(bitmap.sizeInBits()).isEqualTo(131);
        assertThat(bitmap.sizeInBytes()).isGreaterThan(0);
        assertThat(bitmap.getFirstSetBit()).isEqualTo(0);
        assertThat(bitmap.get(64)).isTrue();
        assertThat(bitmap.get(129)).isFalse();
        assertThat(bitmap.toArray()).containsExactly(0, 1, 2, 63, 64, 130);
        assertThat(bitmap.toList()).containsExactly(0, 1, 2, 63, 64, 130);
        assertThat(readAll(bitmap.iterator())).containsExactly(0, 1, 2, 63, 64, 130);
        assertThat(readAll(bitmap.intIterator())).containsExactly(0, 1, 2, 63, 64, 130);
        assertThat(readAll(bitmap.reverseIntIterator())).containsExactly(130, 64, 63, 2, 1, 0);
        assertThat(readFirst(bitmap.clearIntIterator(), 8)).containsExactly(3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(readChunks(bitmap.chunkIterator())).containsExactly(
                new ChunkRun(true, 3),
                new ChunkRun(false, 60),
                new ChunkRun(true, 2),
                new ChunkRun(false, 65),
                new ChunkRun(true, 1)
        );
        assertThat(bitmap.toString()).isEqualTo("{0,1,2,63,64,130}");
    }

    @Test
    void ewahCompressedBitmapSupportsLogicalOperationsAndAggregations() {
        EWAHCompressedBitmap first = EWAHCompressedBitmap.bitmapOf(1, 3, 5, 8);
        EWAHCompressedBitmap second = EWAHCompressedBitmap.bitmapOf(0, 3, 4, 8);
        EWAHCompressedBitmap third = EWAHCompressedBitmap.bitmapOf(3, 5, 8, 9);

        assertThat(first.and(second).toArray()).containsExactly(3, 8);
        assertThat(first.or(second).toArray()).containsExactly(0, 1, 3, 4, 5, 8);
        assertThat(first.xor(second).toArray()).containsExactly(0, 1, 4, 5);
        assertThat(first.andNot(second).toArray()).containsExactly(1, 5);
        assertThat(first.intersects(second)).isTrue();
        assertThat(first.intersects(EWAHCompressedBitmap.bitmapOf(0, 2, 4, 6))).isFalse();
        assertThat(first.andCardinality(second)).isEqualTo(2);
        assertThat(first.orCardinality(second)).isEqualTo(6);
        assertThat(first.xorCardinality(second)).isEqualTo(4);
        assertThat(first.andNotCardinality(second)).isEqualTo(2);

        assertThat(EWAHCompressedBitmap.and(first, second, third).toArray()).containsExactly(3, 8);
        assertThat(EWAHCompressedBitmap.andCardinality(first, second, third)).isEqualTo(2);
        assertThat(EWAHCompressedBitmap.or(first, second, third).toArray()).containsExactly(0, 1, 3, 4, 5, 8, 9);
        assertThat(EWAHCompressedBitmap.orCardinality(first, second, third)).isEqualTo(7);
        assertThat(EWAHCompressedBitmap.xor(first, second, third).toArray()).containsExactly(0, 1, 3, 4, 8, 9);
        assertThat(EWAHCompressedBitmap.threshold(2, first, second, third).toArray()).containsExactly(3, 5, 8);

        assertThat(FastAggregation.bufferedand(16, first, second, third).toArray()).containsExactly(3, 8);
        assertThat(FastAggregation.bufferedor(16, first, second, third).toArray()).containsExactly(0, 1, 3, 4, 5, 8, 9);
        assertThat(FastAggregation.bufferedxor(16, first, second, third).toArray()).containsExactly(0, 1, 3, 4, 8, 9);
    }

    @Test
    void ewahCompressedBitmapSupportsMutationCloningShiftAndSwap() throws CloneNotSupportedException {
        EWAHCompressedBitmap bitmap = new EWAHCompressedBitmap();

        assertThat(bitmap.setSizeInBits(10, false)).isTrue();
        assertThat(bitmap.sizeInBits()).isEqualTo(10);

        bitmap.not();
        assertThat(bitmap.toArray()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        bitmap.clear(4);
        bitmap.clear(7);
        bitmap.set(15);
        assertThat(bitmap.toArray()).containsExactly(0, 1, 2, 3, 5, 6, 8, 9, 15);

        EWAHCompressedBitmap shifted = bitmap.shift(2);
        assertThat(shifted.toArray()).containsExactly(2, 3, 4, 5, 7, 8, 10, 11, 17);

        EWAHCompressedBitmap clone = bitmap.clone();
        assertThat(clone).isEqualTo(bitmap);
        assertThat(clone.hashCode()).isEqualTo(bitmap.hashCode());

        clone.clear(1);
        assertThat(clone.toArray()).containsExactly(0, 2, 3, 5, 6, 8, 9, 15);
        assertThat(bitmap.toArray()).containsExactly(0, 1, 2, 3, 5, 6, 8, 9, 15);

        EWAHCompressedBitmap other = EWAHCompressedBitmap.bitmapOf(100, 130);
        bitmap.swap(other);
        assertThat(bitmap.toArray()).containsExactly(100, 130);
        assertThat(other.toArray()).containsExactly(0, 1, 2, 3, 5, 6, 8, 9, 15);

        bitmap.clear();
        assertThat(bitmap.isEmpty()).isTrue();
        assertThat(bitmap.sizeInBits()).isZero();
    }

    @Test
    void ewahCompressedBitmap32SupportsCoreBitmapOperations() throws CloneNotSupportedException {
        EWAHCompressedBitmap32 first = EWAHCompressedBitmap32.bitmapOf(1, 31, 32, 100);
        EWAHCompressedBitmap32 second = EWAHCompressedBitmap32.bitmapOf(0, 31, 40, 100);
        EWAHCompressedBitmap32 third = EWAHCompressedBitmap32.bitmapOf(31, 32, 40, 90);

        assertThat(first.toArray()).containsExactly(1, 31, 32, 100);
        assertThat(first.cardinality()).isEqualTo(4);
        assertThat(first.sizeInBits()).isEqualTo(101);
        assertThat(first.getFirstSetBit()).isEqualTo(1);
        assertThat(first.get(31)).isTrue();
        assertThat(first.get(30)).isFalse();
        assertThat(readAll(first.intIterator())).containsExactly(1, 31, 32, 100);
        assertThat(readAll(first.reverseIntIterator())).containsExactly(100, 32, 31, 1);
        assertThat(readFirst(first.clearIntIterator(), 6)).containsExactly(0, 2, 3, 4, 5, 6);

        assertThat(first.and(second).toArray()).containsExactly(31, 100);
        assertThat(first.or(second).toArray()).containsExactly(0, 1, 31, 32, 40, 100);
        assertThat(first.xor(second).toArray()).containsExactly(0, 1, 32, 40);
        assertThat(first.andNot(second).toArray()).containsExactly(1, 32);
        assertThat(EWAHCompressedBitmap32.threshold(2, first, second, third).toArray()).containsExactly(31, 32, 40, 100);
        assertThat(first.shift(5).toArray()).containsExactly(6, 36, 37, 105);

        EWAHCompressedBitmap32 clone = first.clone();
        clone.clear(31);
        assertThat(clone.toArray()).containsExactly(1, 32, 100);
        assertThat(first.toArray()).containsExactly(1, 31, 32, 100);
    }

    @Test
    void bitSetSupportsRangeUpdatesAndLogicalOperations() {
        BitSet bitSet = BitSet.bitmapOf(1, 3, 5, 8);

        assertThat(bitSet.cardinality()).isEqualTo(4);
        assertThat(bitSet.size()).isEqualTo(64);
        assertThat(bitSet.empty()).isFalse();
        assertThat(bitSet.get(3)).isTrue();
        assertThat(bitSet.nextSetBit(0)).isEqualTo(1);
        assertThat(bitSet.nextUnsetBit(0)).isEqualTo(0);

        bitSet.set(10, 13);
        bitSet.flip(3, 6);
        assertThat(readAll(bitSet.iterator())).containsExactly(1, 4, 8, 10, 11, 12);
        assertThat(readAll(bitSet.intIterator())).containsExactly(1, 4, 8, 10, 11, 12);
        assertThat(readFirst(bitSet.unsetIntIterator(), 8)).containsExactly(0, 2, 3, 5, 6, 7, 9, 13);

        BitSet other = BitSet.bitmapOf(0, 4, 9, 12);
        BitSet andBitmap = bitSet.clone();
        andBitmap.and(other);
        assertThat(readAll(andBitmap.intIterator())).containsExactly(4, 12);
        assertThat(andBitmap.andcardinality(other)).isEqualTo(2);

        BitSet andNotBitmap = bitSet.clone();
        andNotBitmap.andNot(other);
        assertThat(readAll(andNotBitmap.intIterator())).containsExactly(1, 8, 10, 11);
        assertThat(bitSet.andNotcardinality(other)).isEqualTo(4);

        BitSet orBitmap = bitSet.clone();
        orBitmap.or(other);
        assertThat(readAll(orBitmap.intIterator())).containsExactly(0, 1, 4, 8, 9, 10, 11, 12);
        assertThat(bitSet.orcardinality(other)).isEqualTo(8);

        BitSet xorBitmap = bitSet.clone();
        xorBitmap.xor(other);
        assertThat(readAll(xorBitmap.intIterator())).containsExactly(0, 1, 8, 9, 10, 11);
        assertThat(bitSet.xorcardinality(other)).isEqualTo(6);
        assertThat(bitSet.intersects(other)).isTrue();

        BitSet clone = bitSet.clone();
        assertThat(clone).isEqualTo(bitSet);
        clone.clear(10, 13);
        clone.unset(1);
        assertThat(readAll(clone.intIterator())).containsExactly(4, 8);
        clone.clear();
        assertThat(clone.empty()).isTrue();
    }

    private static List<Integer> readAll(IntIterator iterator) {
        List<Integer> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    private static List<Integer> readAll(Iterator<Integer> iterator) {
        List<Integer> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    private static List<Integer> readFirst(IntIterator iterator, int limit) {
        List<Integer> values = new ArrayList<>();
        while (iterator.hasNext() && values.size() < limit) {
            values.add(iterator.next());
        }
        return values;
    }

    private static List<ChunkRun> readChunks(ChunkIterator iterator) {
        List<ChunkRun> chunks = new ArrayList<>();
        while (iterator.hasNext()) {
            chunks.add(new ChunkRun(iterator.nextBit(), iterator.nextLength()));
            iterator.move();
        }
        return chunks;
    }

    private record ChunkRun(boolean bit, int length) {
    }
}
