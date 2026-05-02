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
    void roundTripSerializesFactoryComparatorAndBackingMap() throws Exception {
        SortedSetMultimap<String, String> original = newCustomSortedSetMultimap();
        original.put("team", "alpha");
        original.put("team", "gamma");
        original.put("team", "beta");
        original.put("team", "alpha");
        original.put("language", "groovy");
        original.put("language", "java");

        assertThat(original.getClass().getName())
                .isEqualTo("com.google.common.collect.Multimaps$CustomSortedSetMultimap");

        SortedSetMultimap<String, String> restored = roundTrip(original);

        assertThat(restored.valueComparator()).isNotNull();
        assertThat(restored.valueComparator().compare("alpha", "beta")).isGreaterThan(0);
        assertThat(restored.get("team")).containsExactly("gamma", "beta", "alpha");
        assertThat(restored.get("language")).containsExactly("java", "groovy");
        assertThat(restored.asMap().keySet()).containsExactly("team", "language");
        assertThat(restored.size()).isEqualTo(5);
    }

    @Test
    void restoredMultimapUsesDeserializedFactoryForNewKeys() throws Exception {
        SortedSetMultimap<String, String> original = newCustomSortedSetMultimap();
        original.put("team", "beta");
        original.put("team", "alpha");

        SortedSetMultimap<String, String> restored = roundTrip(original);
        SortedSet<String> databaseValues = restored.get("database");
        databaseValues.add("mysql");
        databaseValues.add("postgres");
        databaseValues.add("mysql");

        assertThat(restored.get("team")).containsExactly("beta", "alpha");
        assertThat(restored.get("database")).containsExactly("postgres", "mysql");
        assertThat(restored.asMap().keySet()).containsExactly("team", "database");
        assertThat(restored.size()).isEqualTo(4);
    }

    private static SortedSetMultimap<String, String> newCustomSortedSetMultimap() {
        Map<String, Collection<String>> backingMap = new LinkedHashMap<String, Collection<String>>();
        return Multimaps.newSortedSetMultimap(
                backingMap,
                new SerializableReverseOrderStringTreeSetSupplier());
    }

    private static <K, V> SortedSetMultimap<K, V> roundTrip(SortedSetMultimap<K, V> multimap)
            throws IOException, ClassNotFoundException {
        return deserialize(serialize(multimap));
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static <K, V> SortedSetMultimap<K, V> deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (SortedSetMultimap<K, V>) input.readObject();
        }
    }

    private static final class SerializableReverseOrderStringTreeSetSupplier
            implements Supplier<SortedSet<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public SortedSet<String> get() {
            return new TreeSet<String>(Comparator.reverseOrder());
        }
    }
}
