/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Supplier;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomMultimapTest {
    @Test
    void roundTripSerializesFactoryAndBackingMap() throws Exception {
        Multimap<String, Integer> original = newCustomMultimap();
        original.put("alpha", 1);
        original.put("alpha", 2);
        original.put("beta", 3);

        Multimap<String, Integer> restored = roundTrip(original);

        assertThat(restored.asMap().keySet()).containsExactly("alpha", "beta");
        assertThat(restored.get("alpha")).containsExactly(1, 2);
        assertThat(restored.get("beta")).containsExactly(3);
        assertThat(restored.size()).isEqualTo(3);
    }

    @Test
    void restoredMultimapUsesDeserializedFactoryForNewKeys() throws Exception {
        Multimap<String, Integer> original = newCustomMultimap();
        original.put("alpha", 1);

        Multimap<String, Integer> restored = roundTrip(original);
        Collection<Integer> gammaValues = restored.get("gamma");
        gammaValues.add(4);
        gammaValues.add(5);

        assertThat(restored.get("gamma")).containsExactly(4, 5);
        assertThat(restored.asMap().keySet()).containsExactly("alpha", "gamma");
    }

    private static <K, V> Multimap<K, V> newCustomMultimap() {
        Map<K, Collection<V>> backingMap = new LinkedHashMap<K, Collection<V>>();
        return Multimaps.newMultimap(backingMap, new ArrayListFactory<V>());
    }

    private static <K, V> Multimap<K, V> roundTrip(Multimap<K, V> multimap)
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
    private static <K, V> Multimap<K, V> deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (Multimap<K, V>) input.readObject();
        }
    }

    private static final class ArrayListFactory<T>
            implements Supplier<ArrayList<T>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public ArrayList<T> get() {
            return new ArrayList<T>();
        }
    }
}
