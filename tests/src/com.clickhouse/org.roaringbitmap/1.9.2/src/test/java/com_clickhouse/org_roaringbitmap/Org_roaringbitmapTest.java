/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.org_roaringbitmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.roaringbitmap.BatchIterator;
import org.roaringbitmap.BitSetUtil;
import org.roaringbitmap.FastAggregation;
import org.roaringbitmap.FastRankRoaringBitmap;
import org.roaringbitmap.IntConsumer;
import org.roaringbitmap.IntIterator;
import org.roaringbitmap.ParallelAggregation;
import org.roaringbitmap.PeekableIntIterator;
import org.roaringbitmap.PeekableIntRankIterator;
import org.roaringbitmap.RangeBitmap;
import org.roaringbitmap.RoaringBitmap;
import org.roaringbitmap.RoaringBitmapWriter;
import org.roaringbitmap.buffer.BufferBitSetUtil;
import org.roaringbitmap.buffer.BufferFastAggregation;
import org.roaringbitmap.buffer.ImmutableRoaringBitmap;
import org.roaringbitmap.buffer.MutableRoaringBitmap;
import org.roaringbitmap.insights.BitmapAnalyser;
import org.roaringbitmap.insights.BitmapStatistics;
import org.roaringbitmap.longlong.LongIterator;
import org.roaringbitmap.longlong.PeekableLongIterator;
import org.roaringbitmap.longlong.Roaring64Bitmap;
import org.roaringbitmap.longlong.Roaring64NavigableMap;

public class Org_roaringbitmapTest {
    @Test
    void mutableBitmapSupportsSetAlgebraQueriesAndIterators() {
        RoaringBitmap bitmap = RoaringBitmap.bitmapOf(1, 2, 3, 65_536, 65_538);
        bitmap.add(10L, 15L);
        bitmap.remove(12);
        bitmap.flip(2);
        bitmap.flip(70_000L, 70_003L);

        Assertions.assertThat(bitmap.contains(1)).isTrue();
        Assertions.assertThat(bitmap.contains(2)).isFalse();
        Assertions.assertThat(bitmap.contains(10L, 12L)).isTrue();
        Assertions.assertThat(bitmap.intersects(65_536, 65_539)).isTrue();
        Assertions.assertThat(bitmap.getCardinality()).isEqualTo(11);
        Assertions.assertThat(bitmap.rank(11)).isEqualTo(4);
        Assertions.assertThat(bitmap.select(4)).isEqualTo(13);
        Assertions.assertThat(bitmap.first()).isEqualTo(1);
        Assertions.assertThat(bitmap.last()).isEqualTo(70_002);
        Assertions.assertThat(bitmap.nextValue(12)).isEqualTo(13L);
        Assertions.assertThat(bitmap.previousValue(65_537)).isEqualTo(65_536L);
        Assertions.assertThat(bitmap.nextAbsentValue(10)).isEqualTo(12L);
        Assertions.assertThat(bitmap.previousAbsentValue(65_538)).isEqualTo(65_537L);
        Assertions.assertThat(bitmap.rangeCardinality(10, 70_001)).isEqualTo(7L);
        Assertions.assertThat(bitmap.limit(4).toArray()).containsExactly(1, 3, 10, 11);

        PeekableIntIterator iterator = bitmap.getIntIterator();
        Assertions.assertThat(iterator.peekNext()).isEqualTo(1);
        iterator.advanceIfNeeded(65_536);
        Assertions.assertThat(iterator.next()).isEqualTo(65_536);

        IntIterator reverseIterator = bitmap.getReverseIntIterator();
        Assertions.assertThat(reverseIterator.next()).isEqualTo(70_002);

        BatchIterator batchIterator = bitmap.getBatchIterator();
        int[] batch = new int[4];
        int batchSize = batchIterator.nextBatch(batch);
        Assertions.assertThat(Arrays.copyOf(batch, batchSize)).containsExactly(1, 3, 10, 11);

        List<Integer> values = new ArrayList<>();
        IntConsumer collector = values::add;
        bitmap.forEach(collector);
        Assertions.assertThat(values).containsExactly(1, 3, 10, 11, 13, 14, 65_536, 65_538, 70_000, 70_001, 70_002);
    }

    @Test
    void staticOperationsProduceExpectedCardinalitiesAndMembership() {
        RoaringBitmap evens = RoaringBitmap.bitmapOf(0, 2, 4, 6, 8, 65_536);
        RoaringBitmap oddsAndOverlap = RoaringBitmap.bitmapOf(1, 3, 4, 5, 65_536, 65_537);
        RoaringBitmap tail = RoaringBitmap.bitmapOf(8, 9, 10, 65_537);

        Assertions.assertThat(RoaringBitmap.and(evens, oddsAndOverlap).toArray()).containsExactly(4, 65_536);
        Assertions.assertThat(RoaringBitmap.or(evens, oddsAndOverlap).toArray()).containsExactly(0, 1, 2, 3, 4, 5, 6, 8, 65_536, 65_537);
        Assertions.assertThat(RoaringBitmap.xor(evens, oddsAndOverlap).toArray()).containsExactly(0, 1, 2, 3, 5, 6, 8, 65_537);
        Assertions.assertThat(RoaringBitmap.andNot(evens, oddsAndOverlap).toArray()).containsExactly(0, 2, 6, 8);
        Assertions.assertThat(RoaringBitmap.andCardinality(evens, oddsAndOverlap)).isEqualTo(2);
        Assertions.assertThat(RoaringBitmap.orCardinality(evens, oddsAndOverlap)).isEqualTo(10);
        Assertions.assertThat(RoaringBitmap.xorCardinality(evens, oddsAndOverlap)).isEqualTo(8);
        Assertions.assertThat(RoaringBitmap.andNotCardinality(evens, oddsAndOverlap)).isEqualTo(4);
        Assertions.assertThat(RoaringBitmap.intersects(evens, oddsAndOverlap)).isTrue();

        RoaringBitmap offset = RoaringBitmap.addOffset(RoaringBitmap.bitmapOf(0, 1, 65_535), 2);
        Assertions.assertThat(offset.toArray()).containsExactly(2, 3, 65_537);
        Assertions.assertThat(RoaringBitmap.add(evens, 10L, 13L).toArray()).containsExactly(0, 2, 4, 6, 8, 10, 11, 12, 65_536);
        Assertions.assertThat(RoaringBitmap.remove(evens, 2L, 7L).toArray()).containsExactly(0, 8, 65_536);
        Assertions.assertThat(RoaringBitmap.flip(evens, 1L, 5L).toArray()).containsExactly(0, 1, 3, 6, 8, 65_536);
        Assertions.assertThat(RoaringBitmap.or(Arrays.asList(evens, oddsAndOverlap, tail).iterator()).toArray())
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 65_536, 65_537);
    }

    @Test
    void fastAndParallelAggregationsMatchNaiveResults() {
        RoaringBitmap first = RoaringBitmap.bitmapOf(1, 2, 3, 1_000, 65_536, 65_537);
        RoaringBitmap second = RoaringBitmap.bitmapOf(3, 4, 1_000, 65_537, 70_000);
        RoaringBitmap third = RoaringBitmap.bitmapOf(3, 5, 1_000, 65_537, 70_001);
        long[] workShyBuffer = new long[1_024];
        long[] memoryShyBuffer = new long[1_024];

        Assertions.assertThat(FastAggregation.and(first, second, third).toArray()).containsExactly(3, 1_000, 65_537);
        Assertions.assertThat(FastAggregation.workShyAnd(workShyBuffer, first, second, third).toArray()).containsExactly(3, 1_000, 65_537);
        Assertions.assertThat(FastAggregation.workAndMemoryShyAnd(memoryShyBuffer, first, second, third).toArray()).containsExactly(3, 1_000, 65_537);
        Assertions.assertThat(FastAggregation.or(first, second, third).toArray()).containsExactly(1, 2, 3, 4, 5, 1_000, 65_536, 65_537, 70_000, 70_001);
        Assertions.assertThat(FastAggregation.horizontal_or(first, second, third).toArray()).containsExactly(1, 2, 3, 4, 5, 1_000, 65_536, 65_537, 70_000, 70_001);
        Assertions.assertThat(FastAggregation.xor(first, second, third).toArray()).containsExactly(1, 2, 3, 4, 5, 1_000, 65_536, 65_537, 70_000, 70_001);
        Assertions.assertThat(FastAggregation.andCardinality(first, second, third)).isEqualTo(3);
        Assertions.assertThat(FastAggregation.orCardinality(first, second, third)).isEqualTo(10);
        Assertions.assertThat(ParallelAggregation.or(first, second, third).toArray()).containsExactly(1, 2, 3, 4, 5, 1_000, 65_536, 65_537, 70_000, 70_001);
        Assertions.assertThat(ParallelAggregation.xor(first, second, third).toArray()).containsExactly(1, 2, 3, 4, 5, 1_000, 65_536, 65_537, 70_000, 70_001);
    }

    @Test
    void fastRankBitmapMaintainsRankIndexesAcrossMutations() {
        FastRankRoaringBitmap bitmap = new FastRankRoaringBitmap();
        bitmap.add(1, 4);
        bitmap.add(65_536);
        bitmap.add(65_540);
        bitmap.add(70_000, 70_003);

        Assertions.assertThat(bitmap.checkedAdd(4)).isTrue();
        Assertions.assertThat(bitmap.checkedRemove(2)).isTrue();
        bitmap.flip(65_537, 65_540);
        bitmap.remove(70_001);

        Assertions.assertThat(bitmap.toArray())
                .containsExactly(1, 3, 4, 65_536, 65_537, 65_538, 65_539, 65_540, 70_000, 70_002);
        Assertions.assertThat(bitmap.rankLong(65_538)).isEqualTo(6L);
        Assertions.assertThat(bitmap.select(5)).isEqualTo(65_538);

        PeekableIntRankIterator iterator = bitmap.getIntRankIterator();
        Assertions.assertThat(iterator.peekNext()).isEqualTo(1);
        Assertions.assertThat(iterator.peekNextRank()).isEqualTo(1);
        iterator.advanceIfNeeded(65_537);
        Assertions.assertThat(iterator.peekNext()).isEqualTo(65_537);
        Assertions.assertThat(iterator.peekNextRank()).isEqualTo(5);
        Assertions.assertThat(iterator.next()).isEqualTo(65_537);
        Assertions.assertThat(iterator.peekNextRank()).isEqualTo(6);
    }

    @Test
    void writerBuildsBitmapIncrementallyAndCanReset() {
        RoaringBitmapWriter<RoaringBitmap> writer = RoaringBitmapWriter.writer()
                .expectedRange(0, 100_000)
                .expectedDensity(0.1)
                .runCompress(true)
                .get();

        writer.add(1);
        writer.addMany(2, 3);
        writer.add(10, 13);
        writer.add(65_536);
        writer.add(65_537);
        writer.add(70_000);
        writer.flush();

        RoaringBitmap bitmap = writer.get();
        Assertions.assertThat(bitmap.toArray()).containsExactly(1, 2, 3, 10, 11, 12, 65_536, 65_537, 70_000);

        writer.reset();
        writer.addMany(5, 6);
        writer.flush();
        Assertions.assertThat(writer.get().toArray()).containsExactly(5, 6);
    }

    @Test
    void mutableAndImmutableBufferBitmapsSupportOperations() {
        MutableRoaringBitmap first = MutableRoaringBitmap.bitmapOf(1, 2, 3, 65_536);
        first.add(10L, 13L);
        MutableRoaringBitmap second = MutableRoaringBitmap.bitmapOf(3, 4, 10, 65_536, 65_537);
        ImmutableRoaringBitmap immutableFirst = first.toImmutableRoaringBitmap();
        ImmutableRoaringBitmap immutableSecond = second.toImmutableRoaringBitmap();

        Assertions.assertThat(ImmutableRoaringBitmap.and(immutableFirst, immutableSecond).toArray()).containsExactly(3, 10, 65_536);
        Assertions.assertThat(ImmutableRoaringBitmap.or(immutableFirst, immutableSecond).toArray()).containsExactly(1, 2, 3, 4, 10, 11, 12, 65_536, 65_537);
        Assertions.assertThat(ImmutableRoaringBitmap.xor(immutableFirst, immutableSecond).toArray()).containsExactly(1, 2, 4, 11, 12, 65_537);
        Assertions.assertThat(ImmutableRoaringBitmap.andNot(immutableFirst, immutableSecond).toArray()).containsExactly(1, 2, 11, 12);
        Assertions.assertThat(ImmutableRoaringBitmap.andCardinality(immutableFirst, immutableSecond)).isEqualTo(3);
        Assertions.assertThat(ImmutableRoaringBitmap.orCardinality(immutableFirst, immutableSecond)).isEqualTo(9);
        Assertions.assertThat(ImmutableRoaringBitmap.intersects(immutableFirst, immutableSecond)).isTrue();
        Assertions.assertThat(immutableFirst.limit(3).toArray()).containsExactly(1, 2, 3);
        Assertions.assertThat(immutableFirst.rank(10)).isEqualTo(4);
        Assertions.assertThat(immutableFirst.select(4)).isEqualTo(11);

        MutableRoaringBitmap mutableCopy = immutableFirst.toMutableRoaringBitmap();
        mutableCopy.andNot(immutableSecond);
        Assertions.assertThat(mutableCopy.toArray()).containsExactly(1, 2, 11, 12);

        Assertions.assertThat(BufferFastAggregation.and(immutableFirst, immutableSecond).toArray()).containsExactly(3, 10, 65_536);
        Assertions.assertThat(BufferFastAggregation.or(immutableFirst, immutableSecond).toArray()).containsExactly(1, 2, 3, 4, 10, 11, 12, 65_536, 65_537);
        Assertions.assertThat(BufferFastAggregation.xor(immutableFirst, immutableSecond).toArray()).containsExactly(1, 2, 4, 11, 12, 65_537);
    }

    @Test
    void bitSetUtilitiesRoundTripBetweenJdkAndRoaringRepresentations() {
        BitSet bitSet = new BitSet();
        bitSet.set(0);
        bitSet.set(63);
        bitSet.set(64);
        bitSet.set(65_536);

        RoaringBitmap bitmap = BitSetUtil.bitmapOf(bitSet);
        Assertions.assertThat(bitmap.toArray()).containsExactly(0, 63, 64, 65_536);
        Assertions.assertThat(BitSetUtil.equals(bitSet, bitmap)).isTrue();
        Assertions.assertThat(BitSetUtil.bitmapOf(bitSet.toLongArray())).isEqualTo(bitmap);

        MutableRoaringBitmap bufferBitmap = BufferBitSetUtil.bitmapOf(bitSet);
        Assertions.assertThat(bufferBitmap.toArray()).containsExactly(0, 63, 64, 65_536);
        Assertions.assertThat(BufferBitSetUtil.equals(bitSet, bufferBitmap)).isTrue();
        Assertions.assertThat(BufferBitSetUtil.bitmapOf(bitSet.toLongArray())).isEqualTo(bufferBitmap);
    }

    @Test
    void rangeBitmapEvaluatesComparisonsAndRangePredicates() {
        RangeBitmap.Appender appender = RangeBitmap.appender(26);
        long[] values = new long[] {5, 10, 15, 10, 20, 25};
        for (long value : values) {
            appender.add(value);
        }
        RangeBitmap rangeBitmap = appender.build();
        RoaringBitmap evenPositions = RoaringBitmap.bitmapOf(0, 2, 4);

        Assertions.assertThat(rangeBitmap.eq(10).toArray()).containsExactly(1, 3);
        Assertions.assertThat(rangeBitmap.neq(10).toArray()).containsExactly(0, 2, 4, 5);
        Assertions.assertThat(rangeBitmap.lt(15).toArray()).containsExactly(0, 1, 3);
        Assertions.assertThat(rangeBitmap.lte(15).toArray()).containsExactly(0, 1, 2, 3);
        Assertions.assertThat(rangeBitmap.gt(15).toArray()).containsExactly(4, 5);
        Assertions.assertThat(rangeBitmap.gte(15).toArray()).containsExactly(2, 4, 5);
        Assertions.assertThat(rangeBitmap.between(10, 21).toArray()).containsExactly(1, 2, 3, 4);
        Assertions.assertThat(rangeBitmap.eqCardinality(10)).isEqualTo(2L);
        Assertions.assertThat(rangeBitmap.neqCardinality(10)).isEqualTo(4L);
        Assertions.assertThat(rangeBitmap.ltCardinality(15, evenPositions)).isEqualTo(1L);
        Assertions.assertThat(rangeBitmap.lteCardinality(15, evenPositions)).isEqualTo(2L);
        Assertions.assertThat(rangeBitmap.gtCardinality(15, evenPositions)).isEqualTo(1L);
        Assertions.assertThat(rangeBitmap.gteCardinality(15, evenPositions)).isEqualTo(2L);
        Assertions.assertThat(rangeBitmap.betweenCardinality(10, 21, evenPositions)).isEqualTo(2L);
    }

    @Test
    void roaring64BitmapSupportsBoundedIterationAndRangeTraversal() {
        long highValue = 1L << 32;
        long farValue = 1L << 40;
        Roaring64Bitmap bitmap = Roaring64Bitmap.bitmapOf(2L, 5L, highValue, highValue + 3L, farValue);
        bitmap.addRange(10L, 13L);

        Assertions.assertThat(bitmap.toArray()).containsExactly(2L, 5L, 10L, 11L, 12L, highValue, highValue + 3L, farValue);
        Assertions.assertThat(bitmap.contains(highValue + 3L)).isTrue();
        Assertions.assertThat(bitmap.getLongCardinality()).isEqualTo(8L);
        Assertions.assertThat(bitmap.rankLong(11L)).isEqualTo(4L);
        Assertions.assertThat(bitmap.select(5L)).isEqualTo(highValue);
        Assertions.assertThat(bitmap.first()).isEqualTo(2L);
        Assertions.assertThat(bitmap.last()).isEqualTo(farValue);

        PeekableLongIterator fromMiddle = bitmap.getLongIteratorFrom(11L);
        Assertions.assertThat(fromMiddle.peekNext()).isEqualTo(11L);
        Assertions.assertThat(fromMiddle.next()).isEqualTo(11L);
        fromMiddle.advanceIfNeeded(highValue + 1L);
        Assertions.assertThat(fromMiddle.next()).isEqualTo(highValue + 3L);

        PeekableLongIterator reverseFromGap = bitmap.getReverseLongIteratorFrom(highValue + 2L);
        Assertions.assertThat(reverseFromGap.peekNext()).isEqualTo(highValue);
        Assertions.assertThat(reverseFromGap.next()).isEqualTo(highValue);

        List<Long> valuesInRange = new ArrayList<>();
        bitmap.forEachInRange(4L, 10, valuesInRange::add);
        Assertions.assertThat(valuesInRange).containsExactly(5L, 10L, 11L, 12L);
    }

    @Test
    void roaring64NavigableMapHandlesLongValuesAcrossHighKeys() {
        Roaring64NavigableMap first = Roaring64NavigableMap.bitmapOf(1L, 2L, 1L << 32, (1L << 32) + 5L, Long.MAX_VALUE);
        Roaring64NavigableMap second = Roaring64NavigableMap.bitmapOf(2L, 3L, 1L << 32, (1L << 32) + 6L);

        Assertions.assertThat(first.contains(1L << 32)).isTrue();
        Assertions.assertThat(first.getLongCardinality()).isEqualTo(5L);
        Assertions.assertThat(first.select(2)).isEqualTo(1L << 32);
        Assertions.assertThat(first.rankLong((1L << 32) + 5L)).isEqualTo(4L);
        Assertions.assertThat(first.first()).isEqualTo(1L);
        Assertions.assertThat(first.last()).isEqualTo(Long.MAX_VALUE);
        LongIterator forwardIterator = first.getLongIterator();
        Assertions.assertThat(forwardIterator.next()).isEqualTo(1L);
        Assertions.assertThat(forwardIterator.next()).isEqualTo(2L);
        Assertions.assertThat(forwardIterator.next()).isEqualTo(1L << 32);

        Roaring64NavigableMap union = Roaring64NavigableMap.bitmapOf(first.toArray());
        union.or(second);
        Assertions.assertThat(union.toArray()).containsExactly(1L, 2L, 3L, 1L << 32, (1L << 32) + 5L, (1L << 32) + 6L, Long.MAX_VALUE);

        Roaring64NavigableMap intersection = Roaring64NavigableMap.bitmapOf(first.toArray());
        intersection.and(second);
        Assertions.assertThat(intersection.toArray()).containsExactly(2L, 1L << 32);

        Roaring64NavigableMap difference = Roaring64NavigableMap.bitmapOf(first.toArray());
        difference.andNot(second);
        Assertions.assertThat(difference.toArray()).containsExactly(1L, (1L << 32) + 5L, Long.MAX_VALUE);

        first.flip(2L);
        first.removeLong(Long.MAX_VALUE);
        first.addRange(10L, 13L);
        Assertions.assertThat(first.toArray()).containsExactly(1L, 10L, 11L, 12L, 1L << 32, (1L << 32) + 5L);

        LongIterator iterator = first.getReverseLongIterator();
        Assertions.assertThat(iterator.next()).isEqualTo((1L << 32) + 5L);
    }

    @Test
    void analyserReportsContainerStatisticsForRoaringBitmaps() {
        RoaringBitmap first = RoaringBitmap.bitmapOf(1, 2, 65_536, 65_537);
        RoaringBitmap second = RoaringBitmap.bitmapOf(3, 4, 131_072);

        BitmapStatistics firstStats = BitmapAnalyser.analyse(first);
        Assertions.assertThat(firstStats.getBitmapsCount()).isEqualTo(1L);
        Assertions.assertThat(firstStats.containerCount()).isEqualTo(2L);
        Assertions.assertThat(firstStats.containerFraction(1)).isEqualTo(0.5);
        Assertions.assertThat(firstStats.getArrayContainersStats().getContainersCount()).isEqualTo(2L);
        Assertions.assertThat(firstStats.getArrayContainersStats().getCardinalitySum()).isEqualTo(4L);
        Assertions.assertThat(firstStats.getArrayContainersStats().averageCardinality()).isEqualTo(2L);

        BitmapStatistics combinedStats = BitmapAnalyser.analyse(Arrays.asList(first, second));
        Assertions.assertThat(combinedStats.getBitmapsCount()).isEqualTo(2L);
        Assertions.assertThat(combinedStats.containerCount()).isEqualTo(4L);
        Assertions.assertThat(combinedStats.getArrayContainersStats().getCardinalitySum()).isEqualTo(7L);
        Assertions.assertThat(combinedStats.toString()).isNotEmpty();
    }
}
