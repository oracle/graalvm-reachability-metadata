/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntBigArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.Long2DoubleLinkedOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FastutilTest {
    @Test
    void intArrayListSupportsPrimitiveMutationsAndViews() {
        IntArrayList numbers = new IntArrayList();
        numbers.add(7);
        numbers.add(9);
        numbers.add(1, 8);

        assertThat(numbers.toArray(new int[0])).containsExactly(7, 8, 9);
        assertThat(numbers.indexOf(8)).isEqualTo(1);
        assertThat(numbers.lastIndexOf(8)).isEqualTo(1);

        IntListIterator iterator = numbers.listIterator(1);
        assertThat(iterator.nextInt()).isEqualTo(8);
        iterator.set(80);
        iterator.add(81);
        assertThat(numbers.toArray(new int[0])).containsExactly(7, 80, 81, 9);

        assertThat(numbers.removeInt(2)).isEqualTo(81);
        IntList tail = numbers.subList(1, 3);
        assertThat(tail.toArray(new int[0])).containsExactly(80, 9);
        tail.set(0, 800);
        tail.add(900);
        assertThat(numbers.toArray(new int[0])).containsExactly(7, 800, 9, 900);

        int[] copied = new int[2];
        numbers.getElements(1, copied, 0, copied.length);
        assertThat(copied).containsExactly(800, 9);

        numbers.removeElements(1, 3);
        numbers.addElements(1, new int[] {100, 101, 102}, 1, 2);
        assertThat(numbers.toArray(new int[0])).containsExactly(7, 101, 102, 900);
    }

    @Test
    void int2ObjectOpenHashMapUsesPrimitiveKeysAndJava8Remapping() {
        Int2ObjectOpenHashMap<String> labels = new Int2ObjectOpenHashMap<>();
        labels.defaultReturnValue("missing");

        assertThat(labels.get(404)).isEqualTo("missing");
        assertThat(labels.containsKey(404)).isFalse();
        assertThat(labels.put(2, "two")).isEqualTo("missing");
        assertThat(labels.putIfAbsent(2, "ignored")).isEqualTo("two");
        assertThat(labels.computeIfAbsent(5, key -> "value-" + key)).isEqualTo("value-5");
        assertThat(labels.computeIfPresent(2, (key, value) -> value + '-' + key)).isEqualTo("two-2");
        assertThat(labels.merge(2, "tail", (left, right) -> left + '-' + right)).isEqualTo("two-2-tail");

        assertThat(labels.keySet().toIntArray()).containsExactlyInAnyOrder(2, 5);
        assertThat(labels.values()).containsExactlyInAnyOrder("two-2-tail", "value-5");
        assertThat(labels.remove(5, "wrong")).isFalse();
        assertThat(labels.remove(5, "value-5")).isTrue();

        Int2ObjectOpenHashMap<String> clone = labels.clone();
        clone.replace(2, "two-2-tail", "two-replaced");
        assertThat(labels.get(2)).isEqualTo("two-2-tail");
        assertThat(clone.get(2)).isEqualTo("two-replaced");
        clone.trim();

        Int2ObjectMap.Entry<String> entry = clone.int2ObjectEntrySet().iterator().next();
        assertThat(entry.getIntKey()).isEqualTo(2);
        assertThat(entry.getValue()).isEqualTo("two-replaced");
    }

    @Test
    void int2IntOpenHashMapHandlesPrimitiveValuesAndDefaultReturnValue() {
        Int2IntOpenHashMap scores = new Int2IntOpenHashMap();
        scores.defaultReturnValue(-1);

        assertThat(scores.get(10)).isEqualTo(-1);
        assertThat(scores.put(1, 10)).isEqualTo(-1);
        assertThat(scores.addTo(1, 5)).isEqualTo(10);
        assertThat(scores.get(1)).isEqualTo(15);
        assertThat(scores.computeIfAbsent(4, key -> key * 10)).isEqualTo(40);
        assertThat(scores.computeIfPresent(4, (key, value) -> value + key)).isEqualTo(44);
        assertThat(scores.merge(4, 6, (left, right) -> left + right)).isEqualTo(50);

        assertThat(scores.containsKey(1)).isTrue();
        assertThat(scores.containsValue(50)).isTrue();
        assertThat(scores.keySet().toIntArray()).containsExactlyInAnyOrder(1, 4);
        assertThat(scores.values().toIntArray()).containsExactlyInAnyOrder(15, 50);
        assertThat(scores.remove(1, 14)).isFalse();
        assertThat(scores.remove(1, 15)).isTrue();
        assertThat(scores.getOrDefault(1, 123)).isEqualTo(123);
    }

    @Test
    void intAvlTreeSetProvidesSortedRangesAndBidirectionalIteration() {
        IntAVLTreeSet set = new IntAVLTreeSet(new int[] {5, 1, 3, 3, 8});

        assertThat(set.size()).isEqualTo(4);
        assertThat(set.firstInt()).isEqualTo(1);
        assertThat(set.lastInt()).isEqualTo(8);

        IntSortedSet head = set.headSet(5);
        assertThat(head.toIntArray()).containsExactly(1, 3);
        IntSortedSet tail = set.tailSet(3);
        assertThat(tail.toIntArray()).containsExactly(3, 5, 8);
        IntSortedSet middle = set.subSet(2, 8);
        assertThat(middle.toIntArray()).containsExactly(3, 5);

        IntBidirectionalIterator iterator = set.iterator();
        assertThat(iterator.nextInt()).isEqualTo(1);
        assertThat(iterator.nextInt()).isEqualTo(3);
        assertThat(iterator.previousInt()).isEqualTo(3);
        assertThat(iterator.nextInt()).isEqualTo(3);
        assertThat(iterator.nextInt()).isEqualTo(5);
    }

    @Test
    void linkedOpenHashMapMaintainsInsertionOrderAndAccessMovement() {
        Long2DoubleLinkedOpenHashMap readings = new Long2DoubleLinkedOpenHashMap();
        readings.defaultReturnValue(Double.NaN);
        readings.put(10L, 1.0d);
        readings.put(20L, 2.0d);
        readings.put(30L, 3.0d);

        assertThat(readings.firstLongKey()).isEqualTo(10L);
        assertThat(readings.lastLongKey()).isEqualTo(30L);
        assertThat(readings.get(99L)).isNaN();
        assertThat(readings.getAndMoveToFirst(30L)).isEqualTo(3.0d);
        assertThat(readings.firstLongKey()).isEqualTo(30L);
        assertThat(readings.lastLongKey()).isEqualTo(20L);

        assertThat(readings.putAndMoveToLast(10L, 1.5d)).isEqualTo(1.0d);
        assertThat(readings.lastLongKey()).isEqualTo(10L);
        assertThat(readings.computeIfAbsent(40L, key -> key / 10.0d)).isEqualTo(4.0d);
        assertThat(readings.merge(20L, 0.25d, (left, right) -> left + right)).isEqualTo(2.25d);

        assertThat(readings.removeFirstDouble()).isEqualTo(3.0d);
        assertThat(readings.firstLongKey()).isEqualTo(20L);
        assertThat(readings.removeLastDouble()).isEqualTo(4.0d);
        assertThat(readings.lastLongKey()).isEqualTo(10L);
        assertThat(readings.values()).containsExactly(2.25d, 1.5d);
    }

    @Test
    void intHeapPriorityQueueSupportsNaturalAndCustomOrdering() {
        IntHeapPriorityQueue naturalQueue = new IntHeapPriorityQueue(new int[] {4, 1, 3});
        assertThat(naturalQueue.firstInt()).isEqualTo(1);
        naturalQueue.enqueue(0);
        assertThat(naturalQueue.dequeueInt()).isEqualTo(0);
        assertThat(naturalQueue.dequeueInt()).isEqualTo(1);
        assertThat(naturalQueue.dequeueInt()).isEqualTo(3);
        assertThat(naturalQueue.dequeueInt()).isEqualTo(4);
        assertThat(naturalQueue.size()).isZero();

        IntComparator descending = (left, right) -> Integer.compare(right, left);
        IntHeapPriorityQueue maxQueue = new IntHeapPriorityQueue(descending);
        maxQueue.enqueue(4);
        maxQueue.enqueue(9);
        maxQueue.enqueue(7);

        assertThat(maxQueue.comparator()).isSameAs(descending);
        assertThat(maxQueue.firstInt()).isEqualTo(9);
        assertThat(maxQueue.dequeueInt()).isEqualTo(9);
        assertThat(maxQueue.dequeueInt()).isEqualTo(7);
        maxQueue.clear();
        assertThat(maxQueue.size()).isZero();
    }

    @Test
    void intArrayFifoQueueSupportsQueueAndDequeOperations() {
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue(2);
        queue.enqueue(10);
        queue.enqueue(20);
        queue.enqueue(30);

        assertThat(queue.size()).isEqualTo(3);
        assertThat(queue.firstInt()).isEqualTo(10);
        assertThat(queue.lastInt()).isEqualTo(30);
        assertThat(queue.dequeueInt()).isEqualTo(10);

        queue.enqueueFirst(5);
        assertThat(queue.firstInt()).isEqualTo(5);
        assertThat(queue.lastInt()).isEqualTo(30);
        assertThat(queue.dequeueLastInt()).isEqualTo(30);
        assertThat(queue.dequeueInt()).isEqualTo(5);
        assertThat(queue.dequeueInt()).isEqualTo(20);

        queue.enqueue(40);
        queue.enqueue(50);
        queue.clear();
        assertThat(queue.size()).isZero();
    }

    @Test
    void primitiveArrayUtilitiesSortSearchAndResizeWithoutBoxing() {
        int[] values = {5, -1, 5, 0, 3};
        IntArrays.quickSort(values);
        assertThat(values).containsExactly(-1, 0, 3, 5, 5);
        assertThat(IntArrays.binarySearch(values, 3)).isEqualTo(2);

        int[] firstKeys = {2, 1, 2, 1};
        int[] secondKeys = {20, 11, 21, 10};
        IntArrays.quickSort(firstKeys, secondKeys);
        assertThat(firstKeys).containsExactly(1, 1, 2, 2);
        assertThat(secondKeys).containsExactly(10, 11, 20, 21);

        int[] grown = IntArrays.grow(new int[] {1, 2, 3}, 8);
        assertThat(grown.length).isGreaterThanOrEqualTo(8);
        assertThat(IntArrays.trim(grown, 3)).containsExactly(1, 2, 3);
    }

    @Test
    void primitiveBigArrayUtilitiesOperateAcrossLogicalLongIndexes() {
        int[][] big = IntBigArrays.newBigArray(5L);
        IntBigArrays.fill(big, 1L, 4L, 7);
        IntBigArrays.set(big, 4L, 9);
        IntBigArrays.incr(big, 0L);
        IntBigArrays.add(big, 2L, 5);

        assertThat(IntBigArrays.length(big)).isEqualTo(5L);
        assertThat(IntBigArrays.get(big, 0L)).isEqualTo(1);
        assertThat(IntBigArrays.get(big, 1L)).isEqualTo(7);
        assertThat(IntBigArrays.get(big, 2L)).isEqualTo(12);
        assertThat(IntBigArrays.get(big, 4L)).isEqualTo(9);

        int[][] copy = IntBigArrays.copy(big);
        IntBigArrays.swap(copy, 2L, 4L);
        assertThat(IntBigArrays.get(copy, 2L)).isEqualTo(9);
        assertThat(IntBigArrays.get(copy, 4L)).isEqualTo(12);
        assertThat(IntBigArrays.get(big, 2L)).isEqualTo(12);

        int[][] sortable = IntBigArrays.wrap(new int[] {4, 2, 9, 1, 2});
        IntBigArrays.quickSort(sortable);
        assertThat(IntBigArrays.toString(sortable)).isEqualTo("[1, 2, 2, 4, 9]");
        assertThat(IntBigArrays.binarySearch(sortable, 4)).isEqualTo(3L);
    }

    @Test
    void iteratorAdaptersAndListWrappersProvidePrimitiveIteration() {
        IntIterator concatenated = IntIterators.concat(new IntIterator[] {
                IntIterators.fromTo(1, 4),
                IntIterators.singleton(7)
        });
        assertThat(IntIterators.unwrap(concatenated)).containsExactly(1, 2, 3, 7);

        IntArrayList poured = new IntArrayList();
        int count = IntIterators.pour(IntIterators.fromTo(5, 9), poured, 3);
        assertThat(count).isEqualTo(3);
        assertThat(poured.toArray(new int[0])).containsExactly(5, 6, 7);
        assertThat(IntIterators.any(IntIterators.fromTo(1, 5), value -> value == 3)).isTrue();
        assertThat(IntIterators.all(IntIterators.fromTo(1, 5), value -> value > 0)).isTrue();
        assertThat(IntIterators.indexOf(IntIterators.fromTo(10, 15), value -> value == 13)).isEqualTo(3);

        IntList singleton = IntLists.singleton(42);
        assertThat(singleton.getInt(0)).isEqualTo(42);
        IntList unmodifiable = IntLists.unmodifiable(new IntArrayList(new int[] {1, 2}));
        assertThatThrownBy(() -> unmodifiable.add(3)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void binIoRoundTripsPrimitiveArraysAndIterators(@TempDir Path tempDir) throws IOException {
        File file = tempDir.resolve("numbers.bin").toFile();
        BinIO.storeInts(new int[] {6, 7, 8, 9}, 1, 2, file);

        assertThat(BinIO.loadInts(file)).containsExactly(7, 8);
        int[] target = {0, 0, 0, 0};
        assertThat(BinIO.loadInts(file, target, 1, 2)).isEqualTo(2);
        assertThat(target).containsExactly(0, 7, 8, 0);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            BinIO.storeInts(new int[] {3, 1, 4}, output);
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            int[] loaded = new int[3];
            assertThat(BinIO.loadInts(input, loaded)).isEqualTo(3);
            assertThat(loaded).containsExactly(3, 1, 4);
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            IntIterator iterator = BinIO.asIntIterator(input);
            assertThat(iterator.nextInt()).isEqualTo(3);
            assertThat(iterator.nextInt()).isEqualTo(1);
            assertThat(iterator.nextInt()).isEqualTo(4);
            assertThat(iterator.hasNext()).isFalse();
        }
    }
}
