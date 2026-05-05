/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.map.MapWrapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MapWrapperTest {

    @Test
    void serializesAndDeserializesWrappedMap() throws Exception {
        Map<String, Integer> raw = new LinkedHashMap<>();
        raw.put("one", 1);
        raw.put("two", 2);
        MapWrapper<String, Integer> wrapper = new MapWrapper<>(raw);

        byte[] serialized = serialize(wrapper);
        MapWrapper<String, Integer> restored = deserialize(serialized);

        assertThat(restored == wrapper).isFalse();
        assertThat(restored.getRaw()).isInstanceOf(LinkedHashMap.class);
        assertThat(restored.getRaw()).containsExactly(Map.entry("one", 1), Map.entry("two", 2));

        restored.put("three", 3);
        assertThat(restored.get("three")).isEqualTo(3);
        assertThat(wrapper.get("three")).isNull();
    }

    private byte[] serialize(MapWrapper<String, Integer> wrapper) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(wrapper);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private MapWrapper<String, Integer> deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (MapWrapper<String, Integer>) in.readObject();
        }
    }
}
