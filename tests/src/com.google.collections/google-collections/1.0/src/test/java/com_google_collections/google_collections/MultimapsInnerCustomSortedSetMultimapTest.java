/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Supplier;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SortedSetMultimap;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.SortedSet;
import java.util.TreeSet;

public class MultimapsInnerCustomSortedSetMultimapTest {
    @Test
    void customSortedSetMultimapRoundTripSerializesFactoryAndBackingMap() throws Exception {
        SortedSetMultimap<String, String> multimap = Multimaps.newSortedSetMultimap(
                new LinkedHashMap<String, Collection<String>>(), new ReverseSortedSetSupplier());
        multimap.put("letters", "a");
        multimap.put("letters", "c");
        multimap.put("letters", "b");
        multimap.put("letters", "a");
        multimap.put("digits", "one");

        SortedSetMultimap<String, String> restored = roundTrip(multimap);
        restored.put("letters", "d");
        restored.put("new", "value");

        assertThat(restored.get("letters")).containsExactly("d", "c", "b", "a");
        assertThat(restored.get("digits")).containsExactly("one");
        assertThat(restored.get("new")).containsExactly("value");
        assertThat(restored.size()).isEqualTo(6);
        assertThat(restored.asMap()).containsOnlyKeys("letters", "digits", "new");
        assertThat(restored.valueComparator().compare("a", "b")).isPositive();
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }

    private static final class ReverseSortedSetSupplier
            implements Supplier<SortedSet<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public SortedSet<String> get() {
            Comparator<String> comparator = Collections.reverseOrder();
            return new TreeSet<String>(comparator);
        }
    }
}
