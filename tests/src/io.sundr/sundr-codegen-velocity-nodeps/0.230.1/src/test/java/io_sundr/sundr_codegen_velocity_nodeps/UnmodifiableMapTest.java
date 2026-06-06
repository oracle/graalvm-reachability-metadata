/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.map.UnmodifiableMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class UnmodifiableMapTest {

    @Test
    public void serializesAndDeserializesDecoratedMapEntriesInIterationOrder()
            throws IOException, ClassNotFoundException {
        LinkedHashMap<String, Integer> decorated = new LinkedHashMap<>();
        decorated.put("alpha", 1);
        decorated.put("beta", 2);
        decorated.put("gamma", 3);
        Map<String, Integer> original = UnmodifiableMap.decorate(decorated);

        assertThat(original)
                .isInstanceOf(UnmodifiableMap.class)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 2)
                .containsEntry("gamma", 3);
        assertThatThrownBy(() -> original.put("delta", 4))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] serialized = serialize(original);
        Map<String, Integer> restored = deserializeMap(serialized);

        assertThat(restored).isNotSameAs(original).isInstanceOf(UnmodifiableMap.class);
        assertThat(restored)
                .hasSize(3)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 2)
                .containsEntry("gamma", 3);
        assertThat(restored.keySet()).containsExactly("alpha", "beta", "gamma");
        assertThat(restored.values()).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> restored.put("delta", 4))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(restored::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(Map<String, Integer> map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> deserializeMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(Map.class);
            return (Map<String, Integer>) restored;
        }
    }
}
