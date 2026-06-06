/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.map.FixedSizeMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class FixedSizeMapTest {

    @Test
    public void serializesAndDeserializesDecoratedMapStateAndFixedSizeConstraints()
            throws IOException, ClassNotFoundException {
        Map decorated = new LinkedHashMap();
        decorated.put("alpha", 1);
        decorated.put("beta", 2);

        Map original = FixedSizeMap.decorate(decorated);
        original.put("beta", 20);

        byte[] serialized = serialize(original);
        Map restored = deserializeMap(serialized);

        assertThat(restored).isNotSameAs(original).isInstanceOf(FixedSizeMap.class);
        assertThat(restored)
                .hasSize(2)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 20);

        FixedSizeMap fixedSizeMap = (FixedSizeMap) restored;
        assertThat(fixedSizeMap.isFull()).isTrue();
        assertThat(fixedSizeMap.maxSize()).isEqualTo(2);

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

    private static byte[] serialize(Map map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static Map deserializeMap(byte[] serialized) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(Map.class);
            return (Map) restored;
        }
    }
}
