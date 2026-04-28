/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.TreeMap;

import sun.misc.Unsafe;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.SunLimitedUnsafeReflectionProvider;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.core.util.Fields;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeMapConverterTest {
    @Test
    void restoresTreeMapWithCustomComparator() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{ReverseAlphabeticalComparator.class});
        TreeMap<String, String> original = new TreeMap<>(new ReverseAlphabeticalComparator());
        original.put("alpha", "first");
        original.put("bravo", "second");
        original.put("charlie", "third");

        TreeMap<String, String> restoredMap = roundTripTreeMap(xstream, original);

        assertThat(restoredMap.comparator()).isInstanceOf(ReverseAlphabeticalComparator.class);
        assertThat(restoredMap.keySet()).containsExactly("charlie", "bravo", "alpha");
        assertThat(restoredMap).containsEntry("alpha", "first");
        assertThat(restoredMap).containsEntry("bravo", "second");
        assertThat(restoredMap).containsEntry("charlie", "third");
    }

    @Test
    void restoresTreeMapWithNaturalOrdering() {
        XStream xstream = new XStream();
        TreeMap<String, String> original = new TreeMap<>();
        original.put("bravo", "second");
        original.put("alpha", "first");
        original.put("charlie", "third");

        TreeMap<String, String> restoredMap = roundTripTreeMap(xstream, original);

        assertThat(restoredMap.comparator()).isNull();
        assertThat(restoredMap.keySet()).containsExactly("alpha", "bravo", "charlie");
        assertThat(restoredMap).containsEntry("alpha", "first");
        assertThat(restoredMap).containsEntry("bravo", "second");
        assertThat(restoredMap).containsEntry("charlie", "third");
    }

    @Test
    void restoresTreeMapWithCustomComparatorWhenPutAllOptimizationIsUnavailable() {
        withOptimizedTreeMapPutAll(false, () -> {
            XStream xstream = new XStream();
            xstream.allowTypes(new Class[]{ReverseAlphabeticalComparator.class});
            TreeMap<String, String> original = new TreeMap<>(new ReverseAlphabeticalComparator());
            original.put("alpha", "first");
            original.put("bravo", "second");
            original.put("charlie", "third");

            TreeMap<String, String> restoredMap = roundTripTreeMap(xstream, original);

            assertThat(restoredMap.comparator()).isInstanceOf(ReverseAlphabeticalComparator.class);
            assertThat(restoredMap.keySet()).containsExactly("charlie", "bravo", "alpha");
            assertThat(restoredMap).containsEntry("alpha", "first");
            assertThat(restoredMap).containsEntry("bravo", "second");
            assertThat(restoredMap).containsEntry("charlie", "third");
        });
    }

    @SuppressWarnings("unchecked")
    private static TreeMap<String, String> roundTripTreeMap(XStream xstream, TreeMap<String, String> original) {
        Object restored = xstream.fromXML(xstream.toXML(original));

        assertThat(restored).isInstanceOf(TreeMap.class);
        return (TreeMap<String, String>)restored;
    }

    private static void withOptimizedTreeMapPutAll(boolean optimized, Runnable action) {
        Field field = Fields.find(JVM.class, "optimizedTreeMapPutAll");
        Unsafe unsafe = UnsafeAccess.unsafe();
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        boolean previous = unsafe.getBoolean(base, offset);
        unsafe.putBoolean(base, offset, optimized);
        try {
            action.run();
        } finally {
            unsafe.putBoolean(base, offset, previous);
        }
    }

    private static final class UnsafeAccess extends SunLimitedUnsafeReflectionProvider {
        private static Unsafe unsafe() {
            return unsafe;
        }
    }

    public static final class ReverseAlphabeticalComparator implements Comparator<String> {
        @Override
        public int compare(String left, String right) {
            return right.compareTo(left);
        }
    }
}
