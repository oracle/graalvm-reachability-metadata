/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.diffplug.common.collect.Multimaps;
import com.diffplug.common.collect.SortedSetMultimap;

public class MultimapsInnerCustomSortedSetMultimapTest {
    @Test
    void serializesFactoryComparatorAndBackingMapForCustomSortedSetMultimap() throws Exception {
        Map<String, Collection<String>> backingMap = new LinkedHashMap<>();
        SortedSetMultimap<String, String> original = Multimaps.newSortedSetMultimap(
                backingMap,
                new ReverseTreeSetFactory());
        original.put("letters", "a");
        original.put("letters", "c");
        original.put("letters", "b");
        original.put("letters", "b");
        original.put("digits", "1");

        @SuppressWarnings("unchecked")
        SortedSetMultimap<String, String> copy = (SortedSetMultimap<String, String>) roundTrip(original);
        copy.put("letters", "d");
        copy.put("letters", "a");
        copy.put("symbols", "!");

        assertThat(copy.valueComparator()).isInstanceOf(ReverseStringComparator.class);
        assertThat(copy.get("letters")).containsExactly("d", "c", "b", "a");
        assertThat(copy.get("digits")).containsExactly("1");
        assertThat(copy.get("symbols")).containsExactly("!");
        assertThat(copy.asMap().keySet()).containsExactly("letters", "digits", "symbols");
    }

    private static Object roundTrip(Object original) throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return input.readObject();
        }
    }

    private static final class ReverseTreeSetFactory implements Supplier<SortedSet<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public SortedSet<String> get() {
            return new TreeSet<>(new ReverseStringComparator());
        }
    }

    private static final class ReverseStringComparator implements Comparator<String>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int compare(String left, String right) {
            return right.compareTo(left);
        }
    }
}
