/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import static org.assertj.core.api.Assertions.assertThat;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntBigArrays;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class FastutilTest {
    @Test
    void intArrayListSupportsPrimitiveIndexedAndBulkOperations() {
        IntArrayList numbers = new IntArrayList();
        numbers.add(1);
        numbers.add(2);
        numbers.add(3);
        numbers.add(1, 10);
        numbers.addElements(2, new int[] {20, 21, 22}, 0, 2);

        int[] middle = new int[4];
        numbers.getElements(1, middle, 0, middle.length);
        assertThat(middle).containsExactly(10, 20, 21, 2);

        numbers.removeElements(2, 4);
        assertThat(numbers.toIntArray()).containsExactly(1, 10, 2, 3);
        assertThat(numbers.removeInt(1)).isEqualTo(10);
        assertThat(numbers.set(1, 200)).isEqualTo(2);

        IntListIterator iterator = numbers.listIterator(1);
        assertThat(iterator.nextInt()).isEqualTo(200);
        iterator.set(201);
        iterator.add(202);

        assertThat(numbers.toIntArray()).containsExactly(1, 201, 202, 3);
        assertThat(numbers.indexOf(202)).isEqualTo(2);
        assertThat(numbers.lastIndexOf(3)).isEqualTo(3);
    }

    @Test
    void intOpenHashSetHandlesPrimitiveMembershipRemovalAndIteration() {
        IntOpenHashSet set = new IntOpenHashSet(2);
        assertThat(set.add(0)).isTrue();
        assertThat(set.add(42)).isTrue();
        assertThat(set.add(-7)).isTrue();
        assertThat(set.add(42)).isFalse();

        assertThat(set.contains(0)).isTrue();
        assertThat(set.contains(-7)).isTrue();
        assertThat(set.remove(42)).isTrue();
        assertThat(set.contains(42)).isFalse();

        int sum = 0;
        IntIterator iterator = set.iterator();
        while (iterator.hasNext()) {
            sum += iterator.nextInt();
        }

        assertThat(sum).isEqualTo(-7);
        assertThat(set.size()).isEqualTo(2);
        assertThat(set.trim()).isTrue();
    }

    @Test
    void intToIntOpenHashMapAppliesPrimitiveUpdatesAndFunctionalOperations() {
        Int2IntOpenHashMap counts = new Int2IntOpenHashMap();
        counts.defaultReturnValue(-1);

        assertThat(counts.get(7)).isEqualTo(-1);
        assertThat(counts.put(2, 4)).isEqualTo(-1);
        assertThat(counts.addTo(2, 3)).isEqualTo(4);
        assertThat(counts.get(2)).isEqualTo(7);
        assertThat(counts.computeIfAbsent(5, key -> key * 2)).isEqualTo(10);
        assertThat(counts.computeIfPresent(5, (key, value) -> value + key)).isEqualTo(15);
        assertThat(counts.merge(2, 5, Integer::sum)).isEqualTo(12);
        assertThat(counts.replace(2, 12, 20)).isTrue();
        assertThat(counts.remove(5, 15)).isTrue();

        Map<Integer, Integer> entries = new HashMap<>();
        ObjectIterator<Int2IntMap.Entry> iterator = counts.int2IntEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Int2IntMap.Entry entry = iterator.next();
            entries.put(entry.getIntKey(), entry.getIntValue());
        }

        assertThat(entries).containsEntry(2, 20);
        assertThat(counts.keySet().toIntArray()).containsExactly(2);
        assertThat(counts.values().toIntArray()).containsExactly(20);
    }

    @Test
    void objectToIntOpenHashMapMaintainsPrimitiveValuesWithoutBoxingAtCallSites() {
        Object2IntOpenHashMap<String> lengths = new Object2IntOpenHashMap<>();
        lengths.defaultReturnValue(-1);

        assertThat(lengths.getInt("missing")).isEqualTo(-1);
        assertThat(lengths.put("alpha", 5)).isEqualTo(-1);
        assertThat(lengths.addTo("alpha", 2)).isEqualTo(5);
        assertThat(lengths.computeIntIfAbsent("bravo", String::length)).isEqualTo(5);
        assertThat(lengths.computeIntIfPresent("bravo", (key, value) -> value + 10)).isEqualTo(15);
        assertThat(lengths.mergeInt("alpha", 3, Integer::sum)).isEqualTo(10);
        assertThat(lengths.replace("alpha", 10, 11)).isTrue();

        Map<String, Integer> entries = new HashMap<>();
        ObjectIterator<Object2IntMap.Entry<String>> iterator = lengths.object2IntEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Object2IntMap.Entry<String> entry = iterator.next();
            entries.put(entry.getKey(), entry.getIntValue());
        }

        assertThat(entries).containsEntry("alpha", 11).containsEntry("bravo", 15);
        assertThat(lengths.remove("bravo", 15)).isTrue();
        assertThat(lengths.values().toIntArray()).containsExactly(11);
    }

    @Test
    void intToObjectOpenHashMapSupportsSpecializedKeysAndObjectValues() {
        Int2ObjectOpenHashMap<String> labels = new Int2ObjectOpenHashMap<>();
        labels.defaultReturnValue("missing");

        assertThat(labels.get(99)).isEqualTo("missing");
        assertThat(labels.put(1, "one")).isEqualTo("missing");
        assertThat(labels.putIfAbsent(1, "uno")).isEqualTo("one");
        assertThat(labels.computeIfAbsent(2, key -> "two-" + key)).isEqualTo("two-2");
        assertThat(labels.computeIfPresent(2, (key, value) -> value + "!")).isEqualTo("two-2!");
        assertThat(labels.merge(1, "-merged", (oldValue, newValue) -> oldValue + newValue))
                .isEqualTo("one-merged");
        assertThat(labels.replace(1, "one-merged", "ONE")).isTrue();

        Map<Integer, String> entries = new HashMap<>();
        ObjectIterator<Int2ObjectMap.Entry<String>> iterator = labels.int2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Int2ObjectMap.Entry<String> entry = iterator.next();
            entries.put(entry.getIntKey(), entry.getValue());
        }

        assertThat(entries).containsEntry(1, "ONE").containsEntry(2, "two-2!");
        assertThat(labels.remove(2, "two-2!")).isTrue();
        assertThat(labels.values()).containsExactly("ONE");
    }

    @Test
    void sortedSetAndPriorityQueueProvidePrimitiveOrderedContainers() {
        IntRBTreeSet sorted = new IntRBTreeSet(new int[] {5, 1, 3, 9, 7});
        assertThat(sorted.firstInt()).isEqualTo(1);
        assertThat(sorted.lastInt()).isEqualTo(9);

        IntSortedSet window = sorted.subSet(3, 8);
        assertThat(window.toIntArray()).containsExactly(3, 5, 7);

        IntBidirectionalIterator fromFive = sorted.iterator(4);
        assertThat(fromFive.nextInt()).isEqualTo(5);
        assertThat(fromFive.nextInt()).isEqualTo(7);
        assertThat(fromFive.previousInt()).isEqualTo(7);

        IntHeapPriorityQueue minQueue = new IntHeapPriorityQueue();
        minQueue.enqueue(4);
        minQueue.enqueue(1);
        minQueue.enqueue(9);
        assertThat(minQueue.firstInt()).isEqualTo(1);
        assertThat(minQueue.dequeueInt()).isEqualTo(1);

        IntHeapPriorityQueue maxQueue = new IntHeapPriorityQueue(IntComparators.OPPOSITE_COMPARATOR);
        maxQueue.enqueue(4);
        maxQueue.enqueue(1);
        maxQueue.enqueue(9);
        assertThat(maxQueue.dequeueInt()).isEqualTo(9);
        assertThat(maxQueue.dequeueInt()).isEqualTo(4);
        assertThat(maxQueue.dequeueInt()).isEqualTo(1);
    }

    @Test
    void intArraysProvidePrimitiveSortingSearchingAndCapacityUtilities() {
        int[] values = {4, 1, 3, 1, 2};
        IntArrays.quickSort(values);
        assertThat(values).containsExactly(1, 1, 2, 3, 4);
        assertThat(IntArrays.binarySearch(values, 3)).isEqualTo(3);
        assertThat(IntArrays.binarySearch(values, 5)).isEqualTo(-6);

        IntArrays.quickSort(values, IntComparators.OPPOSITE_COMPARATOR);
        assertThat(values).containsExactly(4, 3, 2, 1, 1);
        IntArrays.reverse(values);
        assertThat(values).containsExactly(1, 1, 2, 3, 4);

        int[] primary = {2, 1, 2, 1};
        int[] secondary = {20, 30, 10, 10};
        IntArrays.quickSort(primary, secondary);
        assertThat(primary).containsExactly(1, 1, 2, 2);
        assertThat(secondary).containsExactly(10, 30, 10, 20);

        int[] grown = IntArrays.grow(new int[] {1, 2}, 5);
        assertThat(grown.length).isGreaterThanOrEqualTo(5);
        grown[2] = 8;
        grown[3] = 8;
        grown[4] = 8;
        assertThat(IntArrays.copy(grown, 0, 5)).containsExactly(1, 2, 8, 8, 8);
    }

    @Test
    void intBigArraysManipulateLargeIndexAddressedPrimitiveStorage() {
        int[][] big = IntBigArrays.newBigArray(6);
        IntBigArrays.set(big, 0, 6);
        IntBigArrays.set(big, 1, 5);
        IntBigArrays.set(big, 2, 4);
        IntBigArrays.set(big, 3, 3);
        IntBigArrays.add(big, 3, 10);
        IntBigArrays.mul(big, 2, 2);
        IntBigArrays.incr(big, 4);
        IntBigArrays.decr(big, 5);

        assertThat(IntBigArrays.length(big)).isEqualTo(6);
        assertThat(IntBigArrays.get(big, 2)).isEqualTo(8);
        assertThat(IntBigArrays.get(big, 3)).isEqualTo(13);
        assertThat(IntBigArrays.get(big, 4)).isEqualTo(1);
        assertThat(IntBigArrays.get(big, 5)).isEqualTo(-1);

        int[] compact = new int[6];
        IntBigArrays.copyFromBig(big, 0, compact, 0, compact.length);
        assertThat(compact).containsExactly(6, 5, 8, 13, 1, -1);

        int[] source = {9, 7, 5, 3};
        IntBigArrays.copyToBig(source, 0, big, 1, source.length);
        IntBigArrays.fill(big, 5, 6, 11);
        IntBigArrays.quickSort(big);
        IntBigArrays.copyFromBig(big, 0, compact, 0, compact.length);
        assertThat(compact).containsExactly(3, 5, 6, 7, 9, 11);

        int[][] grown = IntBigArrays.grow(big, 10);
        assertThat(IntBigArrays.length(grown)).isGreaterThanOrEqualTo(10);
        int[][] trimmed = IntBigArrays.trim(grown, 4);
        assertThat(IntBigArrays.length(trimmed)).isEqualTo(4);
    }
}
