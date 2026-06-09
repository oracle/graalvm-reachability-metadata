/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteIterator;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntBigArrays;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class FastutilTest {
    @Test
    void primitiveListsAndArrayUtilitiesUseTypeSpecificAccessors() {
        IntArrayList numbers = new IntArrayList();
        assertTrue(numbers.add(4));
        assertTrue(numbers.add(1));
        assertTrue(numbers.add(9));
        numbers.add(1, 7);

        assertEquals(7, numbers.set(1, 6));
        assertEquals(4, numbers.removeInt(0));
        assertTrue(numbers.rem(9));
        assertArrayEquals(new int[] {6, 1}, numbers.toArray(new int[numbers.size()]));

        int[] inserted = {2, 3, 5};
        numbers.addElements(1, inserted, 0, inserted.length);
        int[] extracted = new int[3];
        numbers.getElements(1, extracted, 0, extracted.length);
        assertArrayEquals(inserted, extracted);

        IntBidirectionalIterator iterator = numbers.listIterator(numbers.size());
        assertEquals(1, iterator.previousInt());
        assertEquals(5, iterator.previousInt());

        int[] backing = {8, 13, 21};
        IntArrayList wrapped = IntArrayList.wrap(backing);
        assertEquals(13, wrapped.set(1, 34));
        assertArrayEquals(new int[] {8, 34, 21}, backing);

        int[] unsorted = {5, -1, 0, 5, 2};
        IntArrays.quickSort(unsorted);
        assertArrayEquals(new int[] {-1, 0, 2, 5, 5}, unsorted);
        assertEquals(2, IntArrays.binarySearch(unsorted, 2));
        IntArrays.reverse(unsorted);
        assertArrayEquals(new int[] {5, 5, 2, 0, -1}, unsorted);
    }

    @Test
    void primitiveObjectMapsSupportDefaultValuesAndFastEntryIteration() {
        Int2ObjectOpenHashMap<String> labels = new Int2ObjectOpenHashMap<>();
        labels.defaultReturnValue("missing");

        assertEquals("missing", labels.get(404));
        assertEquals("missing", labels.put(0, "zero"));
        assertEquals("zero", labels.get(0));
        labels.put(1, "one");
        assertEquals("two", labels.computeIfAbsent(2, key -> "two"));
        assertEquals("one-uno", labels.merge(1, "-uno", String::concat));
        assertTrue(labels.replace(2, "two", "dos"));
        assertEquals("dos", labels.getOrDefault(2, "fallback"));
        assertTrue(labels.remove(0, "zero"));
        assertFalse(labels.containsKey(0));

        Set<String> entries = new HashSet<>();
        ObjectIterator<Int2ObjectMap.Entry<String>> iterator = Int2ObjectMaps.fastIterator(labels);
        while (iterator.hasNext()) {
            Int2ObjectMap.Entry<String> entry = iterator.next();
            entries.add(entry.getIntKey() + "=" + entry.getValue());
        }
        assertEquals(Set.of("1=one-uno", "2=dos"), entries);
        assertTrue(labels.values().contains("dos"));
    }

    @Test
    void sortedSetsAndLinkedMapsPreservePrimitiveOrderingSemantics() {
        IntAVLTreeSet sorted = new IntAVLTreeSet(new int[] {8, 3, 5, 1, 5});

        assertFalse(sorted.add(5));
        assertEquals(1, sorted.firstInt());
        assertEquals(8, sorted.lastInt());

        IntSortedSet middle = sorted.subSet(3, 8);
        assertTrue(middle.contains(3));
        assertTrue(middle.contains(5));
        assertFalse(middle.contains(8));

        IntBidirectionalIterator fromFour = sorted.iterator(4);
        assertEquals(5, fromFour.nextInt());
        assertEquals(5, fromFour.previousInt());

        Int2IntLinkedOpenHashMap linked = new Int2IntLinkedOpenHashMap();
        linked.put(1, 10);
        linked.put(2, 20);
        linked.put(3, 30);
        assertEquals(30, linked.getAndMoveToFirst(3));
        assertEquals(10, linked.putAndMoveToLast(1, 11));

        IntIterator keys = linked.keySet().iterator();
        assertEquals(3, keys.nextInt());
        assertEquals(2, keys.nextInt());
        assertEquals(1, keys.nextInt());
        assertFalse(keys.hasNext());
        assertEquals(30, linked.removeFirstInt());
        assertEquals(11, linked.removeLastInt());
    }

    @Test
    void customObjectStrategiesAndReferenceCollectionsUsePublicHashingApi() {
        Object2IntOpenCustomHashMap<String> counts = new Object2IntOpenCustomHashMap<>(
                new CaseInsensitiveStringStrategy());

        counts.put("Alpha", 4);
        assertEquals(4, counts.addTo("ALPHA", 2));
        assertEquals(1, counts.size());
        assertEquals(6, counts.getInt("alpha"));
        assertEquals(4, counts.computeIntIfAbsent("Beta", String::length));
        assertEquals(10, counts.mergeInt("BETA", 6, Integer::sum));
        assertTrue(counts.containsKey("beta"));
        assertEquals(6, counts.removeInt("ALpha"));

        ObjectArrayList<String> words = new ObjectArrayList<>();
        words.add("first");
        words.add("third");
        words.add(1, "second");
        assertEquals("second", words.get(1));
        assertEquals("third", words.pop());
        assertArrayEquals(new Object[] {"first", "second"}, words.toArray());

        Object shared = new Object();
        String firstText = new String(new char[] {'s', 'a', 'm', 'e'});
        String secondText = new String(new char[] {'s', 'a', 'm', 'e'});
        ReferenceOpenHashSet<Object> identities = new ReferenceOpenHashSet<>();
        assertTrue(identities.add(shared));
        assertTrue(identities.add(firstText));
        assertTrue(identities.add(secondText));
        assertFalse(identities.add(shared));
        assertEquals(3, identities.size());
    }

    @Test
    void bigArraysAndPrimitiveBinaryIoWorkWithoutJavaSerialization() throws IOException {
        int[][] big = IntBigArrays.newBigArray(6);
        IntBigArrays.set(big, 0, 9);
        IntBigArrays.set(big, 1, 4);
        IntBigArrays.set(big, 2, 7);
        IntBigArrays.set(big, 3, 1);
        IntBigArrays.set(big, 4, 3);
        IntBigArrays.set(big, 5, 5);
        IntBigArrays.quickSort(big);

        assertEquals(1, IntBigArrays.get(big, 0));
        assertEquals(9, IntBigArrays.get(big, 5));
        assertEquals(3, IntBigArrays.binarySearch(big, 5));

        int[][] copied = IntBigArrays.newBigArray(6);
        IntBigArrays.copy(big, 0, copied, 0, IntBigArrays.length(big));
        assertTrue(IntBigArrays.equals(big, copied));

        Path path = Files.createTempFile("fastutil-ints", ".bin");
        try {
            File file = path.toFile();
            BinIO.storeInts(new int[] {42, 7, 11}, file);
            assertArrayEquals(new int[] {42, 7, 11}, BinIO.loadInts(file));
        } finally {
            Files.deleteIfExists(path);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DataOutputStream dataOutput = new DataOutputStream(output)) {
            BinIO.storeBytes(ByteArrayList.wrap(new byte[] {1, 2, 3}).iterator(), dataOutput);
        }
        try (DataInputStream dataInput = new DataInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            assertArrayEquals(new byte[] {1, 2, 3}, readAllBytes(BinIO.asByteIterator(dataInput)));
        }
    }

    private static byte[] readAllBytes(ByteIterator iterator) {
        ByteArrayList bytes = new ByteArrayList();
        while (iterator.hasNext()) {
            bytes.add(iterator.nextByte());
        }
        return bytes.toByteArray();
    }

    private static final class CaseInsensitiveStringStrategy implements Hash.Strategy<String> {
        @Override
        public int hashCode(String value) {
            return value == null ? 0 : value.toLowerCase(Locale.ROOT).hashCode();
        }

        @Override
        public boolean equals(String first, String second) {
            if (first == null || second == null) {
                return first == second;
            }
            return first.equalsIgnoreCase(second);
        }
    }
}
