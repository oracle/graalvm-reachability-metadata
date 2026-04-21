/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.collections4.map.FixedSizeMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FixedSizeMapTest {

    @Test
    void serializesAndDeserializesFixedSizeMapStateAndConstraints() throws Exception {
        Map<String, Integer> decorated = new LinkedHashMap<>();
        decorated.put("alpha", 1);
        decorated.put("beta", 2);

        FixedSizeMap<String, Integer> original = FixedSizeMap.fixedSizeMap(decorated);
        original.put("beta", 20);

        byte[] serialized = serialize(original);
        FixedSizeMap<String, Integer> restored = deserializeFixedSizeMap(serialized);

        assertThat(restored)
                .hasSize(2)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 20);
        assertThat(restored.isFull()).isTrue();
        assertThat(restored.maxSize()).isEqualTo(2);

        assertThat(restored.put("alpha", 10)).isEqualTo(1);
        assertThat(restored).containsEntry("alpha", 10);

        assertThatThrownBy(() -> restored.put("gamma", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Map is fixed size");
        assertThatThrownBy(() -> restored.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Map is fixed size");
        assertThatThrownBy(restored::clear)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Map is fixed size");
    }

    private static byte[] serialize(FixedSizeMap<String, Integer> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static FixedSizeMap<String, Integer> deserializeFixedSizeMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(FixedSizeMap.class);
            return (FixedSizeMap<String, Integer>) restored;
        }
    }
}
