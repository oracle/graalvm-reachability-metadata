/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import autovalue.shaded.com.google$.common.base.$Supplier;
import autovalue.shaded.com.google$.common.collect.$Multimaps;
import autovalue.shaded.com.google$.common.collect.$SortedSetMultimap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AutovalueShadedComGoogleInnerCommonCollectInnerMultimapsInnerCustomSortedSetMultimapTest {
    @Test
    void customSortedSetMultimapRestoresFactoryComparatorAndBackingMapAcrossSerializationRoundTrip()
            throws Exception {
        $SortedSetMultimap<String, String> original = $Multimaps.newSortedSetMultimap(
                new LinkedHashMap<>(),
                new SerializableReverseSortedSetSupplier()
        );
        original.put("team", "ada");
        original.put("team", "grace");
        original.put("team", "ada");
        original.put("language", "java");

        assertThat(original.getClass().getName())
                .isEqualTo("autovalue.shaded.com.google$.common.collect.$Multimaps$CustomSortedSetMultimap");
        assertThat(original.valueComparator().compare("ada", "grace")).isGreaterThan(0);

        $SortedSetMultimap<String, String> restored = roundTrip((Serializable) original);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getClass().getName())
                .isEqualTo("autovalue.shaded.com.google$.common.collect.$Multimaps$CustomSortedSetMultimap");
        assertThat(restored.valueComparator().compare("ada", "grace")).isGreaterThan(0);
        assertThat(restored.get("team")).containsExactly("grace", "ada");
        assertThat(restored.get("language")).containsExactly("java");
        assertThat(restored.keySet()).containsExactly("team", "language");

        restored.put("team", "margaret");
        restored.put("team", "ada");
        restored.put("database", "postgres");

        assertThat(restored.get("team")).containsExactly("margaret", "grace", "ada");
        assertThat(restored.get("database")).containsExactly("postgres");
        assertThat(restored.keySet()).containsExactly("team", "language", "database");
    }

    private static $SortedSetMultimap<String, String> roundTrip(
            Serializable value
    ) throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf($SortedSetMultimap.class);
            @SuppressWarnings("unchecked")
            $SortedSetMultimap<String, String> typedRestored = ($SortedSetMultimap<String, String>) restored;
            return typedRestored;
        }
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }

    private static final class SerializableReverseSortedSetSupplier
            implements $Supplier<SortedSet<String>>, Serializable {
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
