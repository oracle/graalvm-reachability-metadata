/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_immutables.value;

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

import org.immutables.value.internal.$guava$.base.$Supplier;
import org.immutables.value.internal.$guava$.collect.$Multimaps;
import org.immutables.value.internal.$guava$.collect.$SortedSetMultimap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MultimapsCustomSortedSetMultimapTest {

    @Test
    void customSortedSetMultimapRestoresComparatorFactoryAndEntriesAcrossRoundTrip() throws Exception {
        $SortedSetMultimap<String, String> original = $Multimaps.newSortedSetMultimap(
                new LinkedHashMap<>(),
                new SerializableReverseOrderStringTreeSetSupplier()
        );
        original.put("team", "alpha");
        original.put("team", "gamma");
        original.put("team", "beta");
        original.put("team", "alpha");
        original.put("language", "groovy");
        original.put("language", "java");

        assertThat(original.getClass().getName())
                .isEqualTo("org.immutables.value.internal.$guava$.collect.$Multimaps$CustomSortedSetMultimap");

        $SortedSetMultimap<String, String> restored = roundTrip((Serializable) original, $SortedSetMultimap.class);

        assertThat(restored.valueComparator()).isNotNull();
        assertThat(restored.valueComparator().compare("alpha", "beta")).isGreaterThan(0);
        assertThat(restored.get("team")).containsExactly("gamma", "beta", "alpha");
        assertThat(restored.get("language")).containsExactly("java", "groovy");
        assertThat(restored.keySet()).containsExactly("team", "language");

        restored.put("team", "delta");
        restored.put("team", "beta");
        restored.put("database", "mysql");
        restored.put("database", "postgres");

        assertThat(restored.get("team")).containsExactly("gamma", "delta", "beta", "alpha");
        assertThat(restored.get("database")).containsExactly("postgres", "mysql");
        assertThat(restored.keySet()).containsExactly("team", "language", "database");
    }

    private static <T> T roundTrip(Serializable value, Class<T> expectedType) throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(expectedType);
            return expectedType.cast(restored);
        }
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }

    private static final class SerializableReverseOrderStringTreeSetSupplier
            implements $Supplier<SortedSet<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public SortedSet<String> get() {
            return new TreeSet<>(Comparator.reverseOrder());
        }
    }
}
