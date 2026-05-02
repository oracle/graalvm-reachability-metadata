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
import com.google.common.collect.SetMultimap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomSetMultimapTest {
    @Test
    void roundTripSerializesFactoryAndBackingMap() throws Exception {
        SetMultimap<String, Integer> original = newCustomSetMultimap();
        original.put("alpha", 1);
        original.put("alpha", 2);
        original.put("alpha", 2);
        original.put("beta", 3);

        SetMultimap<String, Integer> restored = roundTrip(original);

        assertThat(restored.asMap().keySet()).containsExactly("alpha", "beta");
        assertThat(restored.get("alpha")).containsExactly(1, 2);
        assertThat(restored.get("beta")).containsExactly(3);
        assertThat(restored.size()).isEqualTo(3);
    }

    @Test
    void restoredMultimapUsesDeserializedFactoryForNewKeys() throws Exception {
        SetMultimap<String, Integer> original = newCustomSetMultimap();
        original.put("alpha", 1);

        SetMultimap<String, Integer> restored = roundTrip(original);
        Set<Integer> gammaValues = restored.get("gamma");
        gammaValues.add(4);
        gammaValues.add(5);
        gammaValues.add(5);

        assertThat(restored.get("gamma")).containsExactly(4, 5);
        assertThat(restored.asMap().keySet()).containsExactly("alpha", "gamma");
        assertThat(restored.size()).isEqualTo(3);
    }

    private static <K, V> SetMultimap<K, V> newCustomSetMultimap() {
        Map<K, Collection<V>> backingMap = new LinkedHashMap<K, Collection<V>>();
        return Multimaps.newSetMultimap(backingMap, new LinkedHashSetFactory<V>());
    }

    private static <K, V> SetMultimap<K, V> roundTrip(SetMultimap<K, V> multimap)
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
    private static <K, V> SetMultimap<K, V> deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (SetMultimap<K, V>) input.readObject();
        }
    }

    private static final class LinkedHashSetFactory<T>
            implements Supplier<LinkedHashSet<T>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public LinkedHashSet<T> get() {
            return new LinkedHashSet<T>();
        }
    }
}
