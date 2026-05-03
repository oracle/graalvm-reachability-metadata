/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntBigArrays;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.io.FastByteArrayInputStream;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

public class FastutilTest {
    @Test
    void intArrayListSupportsPrimitiveMutationAndListIterators() {
        int[] backingArray = {4, 8, 15, 16};
        IntArrayList list = IntArrayList.wrap(backingArray);

        list.add(2, 23);
        assertThat(list.toIntArray()).containsExactly(4, 8, 23, 15, 16);
        assertThat(list.set(3, 42)).isEqualTo(15);
        assertThat(list.getInt(3)).isEqualTo(42);
        assertThat(list.indexOf(23)).isEqualTo(2);
        assertThat(list.removeInt(1)).isEqualTo(8);
        assertThat(list.rem(16)).isTrue();

        IntListIterator iterator = list.listIterator(1);
        assertThat(iterator.nextInt()).isEqualTo(23);
        iterator.set(24);
        iterator.add(25);
        assertThat(iterator.previousInt()).isEqualTo(25);
        assertThat(iterator.previousInt()).isEqualTo(24);
        assertThat(list.toIntArray()).containsExactly(4, 24, 25, 42);

        int[] slice = new int[3];
        list.getElements(1, slice, 0, 3);
        assertThat(slice).containsExactly(24, 25, 42);
        list.removeElements(1, 3);
        list.addElements(1, new int[] {6, 7, 8}, 0, 3);
        assertThat(list.toIntArray()).containsExactly(4, 6, 7, 8, 42);

        IntArrayList clone = list.clone();
        clone.trim();
        assertThat(clone.toIntArray()).containsExactly(list.toIntArray());
        assertThat(clone.compareTo(list)).isZero();
    }

    @Test
    void primitiveHashCollectionsHandleDefaultValuesAndFastEntryIteration() {
        Int2IntOpenHashMap scores = new Int2IntOpenHashMap();
        scores.defaultReturnValue(-1);

        assertThat(scores.get(99)).isEqualTo(-1);
        assertThat(scores.put(0, 10)).isEqualTo(-1);
        assertThat(scores.computeIfAbsent(7, key -> key * 3)).isEqualTo(21);
        assertThat(scores.computeIfPresent(7, (key, value) -> value + key)).isEqualTo(28);
        assertThat(scores.merge(7, 2, (left, right) -> left * right)).isEqualTo(56);
        assertThat(scores.replace(0, 10, 11)).isTrue();
        assertThat(scores.remove(0, 11)).isTrue();
        assertThat(scores.containsKey(7)).isTrue();
        assertThat(scores.containsValue(56)).isTrue();

        int entrySum = 0;
        for (Int2IntMap.Entry entry : scores.int2IntEntrySet()) {
            entrySum += entry.getIntKey() + entry.getIntValue();
        }
        assertThat(entrySum).isEqualTo(63);

        IntOpenHashSet set = new IntOpenHashSet(new int[] {1, 2, 3, 3, 0});
        assertThat(set.add(4)).isTrue();
        assertThat(set.add(4)).isFalse();
        assertThat(set.remove(0)).isTrue();
        assertThat(set.contains(3)).isTrue();
        assertThat(set.size()).isEqualTo(4);
        assertThat(set.clone()).containsExactlyInAnyOrder(1, 2, 3, 4);
    }

    @Test
    void objectAndPrimitiveMapsExposeTypeSpecificComputeAndWrapperViews() {
        Int2ObjectOpenHashMap<String> names = new Int2ObjectOpenHashMap<>();
        names.put(1, "one");
        names.computeIfAbsent(2, key -> "two");
        names.computeIfPresent(1, (key, value) -> value.toUpperCase());
        names.merge(2, "!", (left, right) -> left + right);

        StringBuilder visited = new StringBuilder();
        Int2ObjectMaps.fastForEach(names, entry -> visited
                .append(entry.getIntKey())
                .append('=')
                .append(entry.getValue())
                .append(';'));
        assertThat(visited.toString()).contains("1=ONE;").contains("2=two!;");
        assertThat(Int2ObjectMaps.unmodifiable(names).get(1)).isEqualTo("ONE");
        assertThatThrownBy(() -> Int2ObjectMaps.unmodifiable(names).put(3, "three"))
                .isInstanceOf(UnsupportedOperationException.class);

        Object2IntOpenHashMap<String> counts = new Object2IntOpenHashMap<>();
        counts.defaultReturnValue(-1);
        counts.put("alpha", 1);
        counts.addTo("alpha", 4);
        counts.computeIntIfAbsent("beta", String::length);
        counts.mergeInt("beta", 5, (left, right) -> left + right);
        assertThat(counts.getInt("alpha")).isEqualTo(5);
        assertThat(counts.getInt("beta")).isEqualTo(9);

        int total = 0;
        for (Object2IntMap.Entry<String> entry : Object2IntMaps.fastIterable(counts)) {
            total += entry.getIntValue();
        }
        assertThat(total).isEqualTo(14);

        Long2DoubleOpenHashMap weights = new Long2DoubleOpenHashMap();
        weights.defaultReturnValue(Double.NaN);
        weights.put(10L, 1.5d);
        weights.addTo(10L, 2.25d);
        weights.computeIfAbsent(20L, key -> key / 10.0d);
        assertThat(weights.get(10L)).isEqualTo(3.75d);
        assertThat(weights.get(20L)).isEqualTo(2.0d);
        assertThat(weights.get(99L)).isNaN();
    }

    @Test
    void customHashCollectionsUseStrategiesForKeyEquivalence() {
        Hash.Strategy<String> caseInsensitiveStrategy = new Hash.Strategy<String>() {
            @Override
            public int hashCode(String value) {
                return value == null ? 0 : value.toLowerCase(Locale.ROOT).hashCode();
            }

            @Override
            public boolean equals(String left, String right) {
                if (left == right) {
                    return true;
                }
                return left != null && right != null && left.equalsIgnoreCase(right);
            }
        };

        ObjectOpenCustomHashSet<String> headers = new ObjectOpenCustomHashSet<>(caseInsensitiveStrategy);
        assertThat(headers.add("Content-Type")).isTrue();
        assertThat(headers.add("content-type")).isFalse();
        assertThat(headers.contains("CONTENT-TYPE")).isTrue();
        assertThat(headers.addOrGet("CoNtEnT-TyPe")).isEqualTo("Content-Type");
        assertThat(headers.addOrGet("Accept")).isEqualTo("Accept");
        assertThat(headers.remove("ACCEPT")).isTrue();
        assertThat(headers).containsExactly("Content-Type");

        Object2IntOpenCustomHashMap<String> counts = new Object2IntOpenCustomHashMap<>(caseInsensitiveStrategy);
        counts.defaultReturnValue(-1);
        assertThat(counts.put("Warning", 1)).isEqualTo(-1);
        assertThat(counts.putIfAbsent("warning", 5)).isEqualTo(1);
        assertThat(counts.addTo("WARNING", 2)).isEqualTo(1);
        assertThat(counts.getInt("warning")).isEqualTo(3);
        assertThat(counts.remove("WaRnInG", 3)).isTrue();
        assertThat(counts.containsKey("warning")).isFalse();
    }

    @Test
    void linkedOpenHashMapMaintainsOrderAndSupportsMoveOperations() {
        Int2ObjectLinkedOpenHashMap<String> map = new Int2ObjectLinkedOpenHashMap<>();
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");

        assertThat(map.getAndMoveToFirst(2)).isEqualTo("two");
        assertThat(map.keySet().toIntArray()).containsExactly(2, 1, 3);
        assertThat(map.putAndMoveToLast(1, "ONE")).isEqualTo("one");
        assertThat(map.keySet().toIntArray()).containsExactly(2, 3, 1);
        assertThat(map.firstIntKey()).isEqualTo(2);
        assertThat(map.lastIntKey()).isEqualTo(1);
        assertThat(map.removeFirst()).isEqualTo("two");
        assertThat(map.removeLast()).isEqualTo("ONE");
        assertThat(map.keySet().toIntArray()).containsExactly(3);
    }

    @Test
    void sortedSetsProvidePrimitiveSubsetAndBidirectionalIteration() {
        IntAVLTreeSet set = new IntAVLTreeSet(new int[] {5, 1, 9, 3, 7});

        assertThat(set.firstInt()).isEqualTo(1);
        assertThat(set.lastInt()).isEqualTo(9);
        assertThat(set.subSet(3, 8).toIntArray()).containsExactly(3, 5, 7);
        assertThat(set.headSet(5).toIntArray()).containsExactly(1, 3);
        assertThat(set.tailSet(5).toIntArray()).containsExactly(5, 7, 9);

        IntBidirectionalIterator iterator = set.iterator();
        assertThat(iterator.nextInt()).isEqualTo(1);
        assertThat(iterator.nextInt()).isEqualTo(3);
        assertThat(iterator.previousInt()).isEqualTo(3);

        IntSortedSet reversed = new IntAVLTreeSet(IntComparators.OPPOSITE_COMPARATOR);
        reversed.add(1);
        reversed.add(5);
        reversed.add(3);
        assertThat(reversed.toIntArray()).containsExactly(5, 3, 1);
        assertThat(reversed.comparator().compare(5, 1)).isLessThan(0);
    }

    @Test
    void arrayUtilitiesSortSearchShuffleAndStabilizePrimitiveArrays() {
        int[] values = {4, 1, 3, 2};
        IntArrays.quickSort(values, (left, right) -> Integer.compare(right, left));
        assertThat(values).containsExactly(4, 3, 2, 1);
        IntArrays.reverse(values);
        assertThat(values).containsExactly(1, 2, 3, 4);
        assertThat(IntArrays.binarySearch(values, 3)).isEqualTo(2);

        int[] keys = {30, 10, 20, 10};
        int[] permutation = {0, 1, 2, 3};
        IntArrays.quickSortIndirect(permutation, keys);
        IntArrays.stabilize(permutation, keys);
        assertThat(permutation).containsExactly(1, 3, 2, 0);

        int[] shuffled = {1, 2, 3, 4, 5};
        IntArrays.shuffle(shuffled, new Random(1234L));
        assertThat(shuffled).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
        IntArrays.radixSort(shuffled);
        assertThat(shuffled).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void bigArrayUtilitiesOperateAcrossPrimitiveBigArrayApi() {
        int[][] bigArray = IntBigArrays.newBigArray(6);
        IntBigArrays.copyToBig(new int[] {4, 3, 2, 1}, 0, bigArray, 0, 4);
        assertThat(IntBigArrays.length(bigArray)).isEqualTo(6L);

        IntBigArrays.quickSort(bigArray, 0, 4);
        assertThat(IntBigArrays.binarySearch(bigArray, 0, 4, 3)).isEqualTo(2L);
        IntBigArrays.add(bigArray, 0, 9);
        IntBigArrays.mul(bigArray, 1, 5);
        IntBigArrays.incr(bigArray, 2);
        IntBigArrays.decr(bigArray, 3);
        IntBigArrays.fill(bigArray, 4, 6, 7);

        int[] snapshot = new int[6];
        IntBigArrays.copyFromBig(bigArray, 0, snapshot, 0, snapshot.length);
        assertThat(snapshot).containsExactly(10, 10, 4, 3, 7, 7);

        int[][] wrapped = IntBigArrays.wrap(new int[] {10, 10, 4, 3, 7, 7});
        assertThat(IntBigArrays.equals(bigArray, wrapped)).isTrue();
    }

    @Test
    void priorityQueuesExposePrimitiveFifoAndHeapBehavior() {
        IntArrayFIFOQueue fifoQueue = new IntArrayFIFOQueue(2);
        fifoQueue.enqueue(2);
        fifoQueue.enqueue(3);
        fifoQueue.enqueueFirst(1);
        assertThat(fifoQueue.firstInt()).isEqualTo(1);
        assertThat(fifoQueue.lastInt()).isEqualTo(3);
        assertThat(fifoQueue.dequeueInt()).isEqualTo(1);
        assertThat(fifoQueue.dequeueLastInt()).isEqualTo(3);
        assertThat(fifoQueue.dequeueInt()).isEqualTo(2);

        IntHeapPriorityQueue heapQueue = new IntHeapPriorityQueue(IntComparators.OPPOSITE_COMPARATOR);
        heapQueue.enqueue(4);
        heapQueue.enqueue(9);
        heapQueue.enqueue(1);
        assertThat(heapQueue.firstInt()).isEqualTo(9);
        assertThat(heapQueue.dequeueInt()).isEqualTo(9);
        assertThat(heapQueue.dequeueInt()).isEqualTo(4);
        assertThat(heapQueue.dequeueInt()).isEqualTo(1);
    }

    @Test
    void iteratorAndListUtilitiesAdaptAndProtectPrimitiveCollections() {
        IntIterator concatenated = IntIterators.concat(new IntIterator[] {
                IntIterators.wrap(new int[] {1, 2}),
                IntIterators.fromTo(3, 6)
        });
        assertThat(IntIterators.unwrap(concatenated)).containsExactly(1, 2, 3, 4, 5);

        assertThat(IntIterators.any(IntIterators.wrap(new int[] {1, 2, 3}), value -> value == 2)).isTrue();
        assertThat(IntIterators.all(IntIterators.wrap(new int[] {2, 4, 6}), value -> value % 2 == 0)).isTrue();
        assertThat(IntIterators.indexOf(IntIterators.wrap(new int[] {5, 6, 7}), value -> value > 5)).isEqualTo(1);

        IntList singleton = IntLists.singleton(42);
        assertThat(singleton.getInt(0)).isEqualTo(42);
        IntList synchronizedList = IntLists.synchronize(new IntArrayList(new int[] {1, 2}));
        synchronizedList.add(3);
        assertThat(synchronizedList.toIntArray()).containsExactly(1, 2, 3);

        IntList unmodifiable = IntLists.unmodifiable(new IntArrayList(new int[] {7, 8}));
        assertThat(unmodifiable.toIntArray()).containsExactly(7, 8);
        assertThatThrownBy(() -> unmodifiable.add(9)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void fastByteArrayStreamsSupportPositioningMarkingAndBufferReuse() throws IOException {
        FastByteArrayOutputStream output = new FastByteArrayOutputStream(2);
        output.write("abcd".getBytes(StandardCharsets.UTF_8));
        output.position(1);
        output.write('Z');
        output.position(output.length());
        output.write('e');
        output.trim();

        byte[] bytes = Arrays.copyOf(output.array, output.length);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("aZcde");

        FastByteArrayInputStream input = new FastByteArrayInputStream(bytes);
        assertThat(input.length()).isEqualTo(5L);
        assertThat(input.read()).isEqualTo('a');
        input.mark(10);
        assertThat(input.read()).isEqualTo('Z');
        assertThat(input.skip(2)).isEqualTo(2L);
        assertThat(input.read()).isEqualTo('e');
        input.reset();
        byte[] tail = new byte[4];
        assertThat(input.read(tail, 0, tail.length)).isEqualTo(4);
        assertThat(new String(tail, StandardCharsets.UTF_8)).isEqualTo("Zcde");
        input.position(2);
        assertThat(input.read()).isEqualTo('c');
        input.close();
        output.close();
    }
}
