/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntBigArrays;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.Long2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FastutilTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void intArrayListSupportsPrimitiveMutationViewsAndIterators() {
        IntArrayList numbers = new IntArrayList();
        numbers.add(3);
        numbers.add(1);
        numbers.add(4);
        numbers.add(1, 9);
        numbers.addElements(2, new int[] {2, 6, 5, 3}, 1, 2);

        assertThat(numbers.toIntArray()).containsExactly(3, 9, 6, 5, 1, 4);
        assertThat(numbers.indexOf(9)).isEqualTo(1);
        assertThat(numbers.lastIndexOf(4)).isEqualTo(5);

        int previous = numbers.set(3, 8);
        int removedByIndex = numbers.removeInt(0);
        boolean removedByValue = numbers.rem(1);

        assertThat(previous).isEqualTo(5);
        assertThat(removedByIndex).isEqualTo(3);
        assertThat(removedByValue).isTrue();
        assertThat(numbers.toIntArray()).containsExactly(9, 6, 8, 4);

        int[] copied = new int[3];
        numbers.getElements(1, copied, 0, 3);
        assertThat(copied).containsExactly(6, 8, 4);

        IntList middle = numbers.subList(1, 3);
        middle.set(0, 7);
        middle.add(11);
        middle.removeElements(1, 2);
        assertThat(numbers.toIntArray()).containsExactly(9, 7, 11, 4);

        IntListIterator iterator = numbers.listIterator(1);
        assertThat(iterator.nextInt()).isEqualTo(7);
        iterator.set(70);
        iterator.add(71);
        assertThat(iterator.previousInt()).isEqualTo(71);
        assertThat(numbers.toIntArray()).containsExactly(9, 70, 71, 11, 4);

        int[] backingArray = new int[] {10, 20, 30};
        IntArrayList wrapped = IntArrayList.wrap(backingArray);
        wrapped.set(1, 25);
        assertThat(backingArray).containsExactly(10, 25, 30);

        IntList unmodifiable = IntLists.unmodifiable(numbers);
        assertThat(unmodifiable.getInt(2)).isEqualTo(71);
        assertThatThrownBy(() -> unmodifiable.add(99)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void primitiveArrayAlgorithmsSortSearchAndReorderData() {
        int[] values = new int[] {5, -1, 9, 5, 0, 3};
        IntArrays.quickSort(values);
        assertThat(values).containsExactly(-1, 0, 3, 5, 5, 9);
        assertThat(IntArrays.binarySearch(values, 3)).isEqualTo(2);
        assertThat(IntArrays.binarySearch(values, 4)).isEqualTo(-4);

        IntArrays.reverse(values, 1, 5);
        assertThat(values).containsExactly(-1, 5, 5, 3, 0, 9);

        int[] descending = new int[] {4, 7, 2, 9};
        IntArrays.quickSort(descending, (left, right) -> Integer.compare(right, left));
        assertThat(descending).containsExactly(9, 7, 4, 2);
        assertThat(IntArrays.binarySearch(descending, 7, (left, right) -> Integer.compare(right, left))).isEqualTo(1);

        int[] primary = new int[] {2, 1, 2, 1, 2};
        int[] secondary = new int[] {30, 40, 10, 20, 20};
        IntArrays.radixSort(primary, secondary);
        assertThat(primary).containsExactly(1, 1, 2, 2, 2);
        assertThat(secondary).containsExactly(20, 40, 10, 20, 30);

        int[] permutation = new int[] {0, 1, 2, 3, 4};
        int[] keys = new int[] {40, 10, 40, 20, 10};
        IntArrays.quickSortIndirect(permutation, keys);
        IntArrays.stabilize(permutation, keys);
        assertThat(permutation).containsExactly(1, 4, 3, 0, 2);
    }

    @Test
    void intOpenHashSetHandlesPrimitiveMembershipGrowthAndRemoval() {
        IntOpenHashSet set = new IntOpenHashSet(2);
        for (int value = -50; value <= 50; value += 5) {
            assertThat(set.add(value)).isTrue();
        }

        assertThat(set.add(0)).isFalse();
        assertThat(set.contains(-45)).isTrue();
        assertThat(set.contains(46)).isFalse();
        assertThat(set.remove(-45)).isTrue();
        assertThat(set.contains(-45)).isFalse();
        assertThat(set.trim()).isTrue();

        IntArrayList iterated = new IntArrayList(set.iterator());
        IntArrays.quickSort(iterated.elements(), 0, iterated.size());
        assertThat(iterated.toIntArray()).containsExactly(-50, -40, -35, -30, -25, -20, -15, -10, -5, 0, 5, 10,
                15, 20, 25, 30, 35, 40, 45, 50);
    }

    @Test
    void intToObjectOpenHashMapUsesPrimitiveKeysDefaultValuesAndFastEntries() {
        Int2ObjectOpenHashMap<String> map = new Int2ObjectOpenHashMap<>(4);
        map.defaultReturnValue("missing");

        assertThat(map.get(42)).isEqualTo("missing");
        assertThat(map.put(0, "zero")).isEqualTo("missing");
        assertThat(map.put(10, "ten")).isEqualTo("missing");
        assertThat(map.putIfAbsent(10, "TEN")).isEqualTo("ten");
        assertThat(map.computeIfAbsent(20, key -> "twenty-" + key)).isEqualTo("twenty-20");
        assertThat(map.computeIfPresent(10, (key, value) -> value + "!")).isEqualTo("ten!");
        assertThat(map.merge(10, " again", (left, right) -> left + right)).isEqualTo("ten! again");
        assertThat(map.replace(0, "zero", "ZERO")).isTrue();
        assertThat(map.remove(20, "twenty-20")).isTrue();

        List<String> entries = new ArrayList<>();
        ObjectIterator<Int2ObjectMap.Entry<String>> iterator = Int2ObjectMaps.fastIterator(map);
        while (iterator.hasNext()) {
            Int2ObjectMap.Entry<String> entry = iterator.next();
            entries.add(entry.getIntKey() + "=" + entry.getValue());
        }

        assertThat(entries).containsExactlyInAnyOrder("0=ZERO", "10=ten! again");
        assertThat(map.keySet().toIntArray()).containsExactlyInAnyOrder(0, 10);
        assertThat(map.values()).containsExactlyInAnyOrder("ZERO", "ten! again");
        assertThat(map.trim()).isTrue();
    }

    @Test
    void objectToIntOpenHashMapUsesPrimitiveValuesAndNullKeys() {
        Object2IntOpenHashMap<String> frequencies = new Object2IntOpenHashMap<>();
        frequencies.defaultReturnValue(-1);

        assertThat(frequencies.getInt("absent")).isEqualTo(-1);
        assertThat(frequencies.put("alpha", 2)).isEqualTo(-1);
        assertThat(frequencies.addTo("alpha", 3)).isEqualTo(2);
        assertThat(frequencies.getInt("alpha")).isEqualTo(5);
        assertThat(frequencies.computeIntIfAbsent("beta", key -> key.length())).isEqualTo(4);
        assertThat(frequencies.computeIntIfPresent("beta", (key, value) -> value * 2)).isEqualTo(8);
        assertThat(frequencies.mergeInt("alpha", 10, (left, right) -> left + right)).isEqualTo(15);
        assertThat(frequencies.put(null, 100)).isEqualTo(-1);
        assertThat(frequencies.containsKey(null)).isTrue();
        assertThat(frequencies.removeInt(null)).isEqualTo(100);

        assertThat(frequencies.values().toIntArray()).containsExactlyInAnyOrder(15, 8);
        assertThat(frequencies.keySet()).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(frequencies.replace("alpha", 15, 16)).isTrue();
        assertThat(frequencies.getInt("alpha")).isEqualTo(16);
    }

    @Test
    void avlTreeSetProvidesSortedPrimitiveRangesAndBidirectionalIteration() {
        IntAVLTreeSet set = new IntAVLTreeSet(new int[] {8, 3, 5, 1, 13, 8, 21, 2});

        assertThat(set.size()).isEqualTo(7);
        assertThat(set.firstInt()).isEqualTo(1);
        assertThat(set.lastInt()).isEqualTo(21);
        assertThat(set.contains(13)).isTrue();

        IntSortedSet head = set.headSet(8);
        IntSortedSet tail = set.tailSet(8);
        IntSortedSet subset = set.subSet(2, 13);
        assertThat(head.toIntArray()).containsExactly(1, 2, 3, 5);
        assertThat(tail.toIntArray()).containsExactly(8, 13, 21);
        assertThat(subset.toIntArray()).containsExactly(2, 3, 5, 8);

        IntBidirectionalIterator iterator = set.iterator(4);
        assertThat(iterator.nextInt()).isEqualTo(5);
        assertThat(iterator.nextInt()).isEqualTo(8);
        assertThat(iterator.previousInt()).isEqualTo(8);

        assertThat(set.remove(3)).isTrue();
        assertThat(set.toIntArray()).containsExactly(1, 2, 5, 8, 13, 21);
    }

    @Test
    void linkedLongToDoubleMapMaintainsAndUpdatesIterationOrder() {
        Long2DoubleLinkedOpenHashMap map = new Long2DoubleLinkedOpenHashMap();
        map.defaultReturnValue(Double.NaN);

        map.put(10L, 1.5D);
        map.put(20L, 2.5D);
        map.putAndMoveToFirst(30L, 3.5D);
        map.putAndMoveToLast(5L, 0.5D);

        assertThat(map.firstLongKey()).isEqualTo(30L);
        assertThat(map.lastLongKey()).isEqualTo(5L);
        assertThat(map.getAndMoveToLast(30L)).isEqualTo(3.5D);
        assertThat(map.lastLongKey()).isEqualTo(30L);
        assertThat(map.getAndMoveToFirst(20L)).isEqualTo(2.5D);
        assertThat(map.firstLongKey()).isEqualTo(20L);

        assertThat(map.addTo(10L, 2.0D)).isEqualTo(1.5D);
        assertThat(map.get(10L)).isEqualTo(3.5D);
        assertThat(map.computeIfAbsent(40L, key -> key / 10.0D)).isEqualTo(4.0D);
        assertThat(map.merge(40L, 1.0D, (left, right) -> left + right)).isEqualTo(5.0D);
        assertThat(map.removeFirstDouble()).isEqualTo(2.5D);
        assertThat(map.removeLastDouble()).isEqualTo(5.0D);
        assertThat(map.keySet().toLongArray()).containsExactly(10L, 5L, 30L);
    }

    @Test
    void bigArraysProvideSegmentedPrimitiveOperations() {
        int[][] bigArray = IntBigArrays.wrap(new int[] {7, 6, 5, 4, 3, 2, 1});
        assertThat(IntBigArrays.length(bigArray)).isEqualTo(7L);

        IntBigArrays.set(bigArray, 3L, 40);
        IntBigArrays.incr(bigArray, 4L);
        IntBigArrays.add(bigArray, 5L, 8);
        IntBigArrays.swap(bigArray, 0L, 6L);
        assertThat(IntBigArrays.get(bigArray, 0L)).isEqualTo(1);
        assertThat(IntBigArrays.get(bigArray, 3L)).isEqualTo(40);
        assertThat(IntBigArrays.get(bigArray, 4L)).isEqualTo(4);
        assertThat(IntBigArrays.get(bigArray, 5L)).isEqualTo(10);
        assertThat(IntBigArrays.get(bigArray, 6L)).isEqualTo(7);

        int[][] copy = IntBigArrays.newBigArray(7L);
        IntBigArrays.copy(bigArray, 0L, copy, 0L, 7L);
        IntBigArrays.quickSort(copy);
        assertThat(IntBigArrays.binarySearch(copy, 10)).isEqualTo(5L);

        int[] small = new int[4];
        IntBigArrays.copyFromBig(copy, 1L, small, 0, small.length);
        assertThat(small).containsExactly(4, 5, 6, 7);

        int[][] target = IntBigArrays.newBigArray(4L);
        IntBigArrays.copyToBig(new int[] {11, 12, 13, 14}, 0, target, 0L, 4L);
        IntBigArrays.fill(target, 1L, 3L, 99);
        assertThat(IntBigArrays.toString(target)).isEqualTo("[11, 99, 99, 14]");
    }

    @Test
    void binIoStoresLoadsAndStreamsPrimitiveValues() throws IOException {
        File intFile = temporaryDirectory.resolve("numbers.bin").toFile();
        int[] numbers = new int[] {11, 22, 33, 44, 55};
        BinIO.storeInts(numbers, 1, 3, intFile);

        int[] loaded = BinIO.loadInts(intFile);
        assertThat(loaded).containsExactly(22, 33, 44);

        IntIterator fileIterator = BinIO.asIntIterator(intFile);
        IntArrayList iterated = new IntArrayList(fileIterator);
        assertThat(iterated.toIntArray()).containsExactly(22, 33, 44);

        ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
        try (DataOutputStream dataOutput = new DataOutputStream(byteOutput)) {
            BinIO.storeInts(IntArrayList.wrap(new int[] {3, 1, 4}).iterator(), dataOutput);
        }

        int[] streamed = new int[3];
        try (DataInputStream dataInput = new DataInputStream(new ByteArrayInputStream(byteOutput.toByteArray()))) {
            assertThat(BinIO.loadInts(dataInput, streamed)).isEqualTo(3);
        }
        assertThat(streamed).containsExactly(3, 1, 4);
    }
}
