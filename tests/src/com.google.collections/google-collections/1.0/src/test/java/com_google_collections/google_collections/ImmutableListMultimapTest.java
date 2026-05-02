/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableListMultimap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class ImmutableListMultimapTest {
    @Test
    void serializedImmutableListMultimapRestoresKeysValuesAndOrder() throws Exception {
        ImmutableListMultimap<String, String> original = ImmutableListMultimap.<String, String>builder()
                .putAll("letters", "alpha", "beta", "gamma")
                .putAll("numbers", "one", "two")
                .build();

        ImmutableListMultimap<String, String> restored = roundTrip(original);

        assertThat(restored.keySet()).containsExactly("letters", "numbers");
        assertThat(restored.get("letters")).containsExactly("alpha", "beta", "gamma");
        assertThat(restored.get("numbers")).containsExactly("one", "two");
        assertThat(restored.size()).isEqualTo(5);
    }

    @Test
    void deserializedImmutableListMultimapRemainsImmutable() throws Exception {
        ImmutableListMultimap<String, Integer> original = ImmutableListMultimap.<String, Integer>builder()
                .putAll("scores", 10, 20)
                .build();

        ImmutableListMultimap<String, Integer> restored = roundTrip(original);

        assertThatThrownBy(() -> restored.put("scores", 30))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.get("scores").add(30))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static <K, V> ImmutableListMultimap<K, V> roundTrip(ImmutableListMultimap<K, V> value)
            throws IOException, ClassNotFoundException {
        return deserialize(serialize(value));
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static <K, V> ImmutableListMultimap<K, V> deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ImmutableListMultimap<K, V>) input.readObject();
        }
    }
}
