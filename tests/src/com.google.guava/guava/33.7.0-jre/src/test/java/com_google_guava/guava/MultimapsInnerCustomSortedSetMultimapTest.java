/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Supplier;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SortedSetMultimap;
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
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomSortedSetMultimapTest {
    @Test
    void serializesCustomSortedSetMultimapFactoryAndBackingMap() throws Exception {
        Map<String, Collection<String>> backingMap = new LinkedHashMap<String, Collection<String>>();
        SortedSetMultimap<String, String> original = Multimaps.newSortedSetMultimap(
                backingMap,
                new SerializableReverseTreeSetSupplier());
        original.put("letters", "b");
        original.put("letters", "a");
        original.put("letters", "b");
        original.put("numbers", "one");

        SortedSetMultimap<String, String> copy = roundTrip(original);

        assertThat(copy.get("letters")).containsExactly("b", "a");
        assertThat(copy.get("numbers")).containsExactly("one");
        assertThat(copy.keySet()).containsExactly("letters", "numbers");
        assertThat(copy.valueComparator()).isInstanceOf(ReverseStringComparator.class);

        copy.put("created-after-deserialization", "alpha");
        copy.put("created-after-deserialization", "omega");
        copy.put("created-after-deserialization", "alpha");
        assertThat(copy.get("created-after-deserialization"))
                .containsExactly("omega", "alpha");
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream input = new ObjectInputStream(inputBytes)) {
            return (T) input.readObject();
        }
    }

    private static final class SerializableReverseTreeSetSupplier
            implements Supplier<SortedSet<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public SortedSet<String> get() {
            return new TreeSet<String>(new ReverseStringComparator());
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
