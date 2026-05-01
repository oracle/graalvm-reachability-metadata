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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MapWrapperTest {
    @Test
    public void serializesAndDeserializesWrappedMap() throws Exception {
        Map<String, Integer> raw = new LinkedHashMap<>();
        raw.put("first", 1);
        raw.put("second", 2);
        MapWrapper<String, Integer> original = new MapWrapper<>(raw);

        byte[] serialized = serialize(original);
        MapWrapper<String, Integer> restored = deserializeMapWrapper(serialized);

        assertThat((Object) restored).isNotSameAs(original);
        assertThat((Map<String, Integer>) restored).containsEntry("first", 1).containsEntry("second", 2);
        assertThat(restored.getRaw()).isInstanceOf(LinkedHashMap.class);
        assertThat(restored.keySet()).containsExactly("first", "second");
    }

    private static byte[] serialize(MapWrapper<String, Integer> wrapper) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(wrapper);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static MapWrapper<String, Integer> deserializeMapWrapper(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(MapWrapper.class);
            return (MapWrapper<String, Integer>) restored;
        }
    }
}
