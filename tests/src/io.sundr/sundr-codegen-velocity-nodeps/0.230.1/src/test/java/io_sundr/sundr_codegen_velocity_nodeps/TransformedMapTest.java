/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.Transformer;
import io.sundr.deps.org.apache.commons.collections.TransformerUtils;
import io.sundr.deps.org.apache.commons.collections.map.TransformedMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TransformedMapTest {

    @Test
    public void serializesTransformedMapStateAndTransformerBehavior()
            throws IOException, ClassNotFoundException {
        Map decorated = new LinkedHashMap();
        decorated.put(Integer.valueOf(1), Integer.valueOf(10));

        Transformer stringValueTransformer = TransformerUtils.stringValueTransformer();
        Map original = TransformedMap.decorateTransform(
                decorated,
                stringValueTransformer,
                stringValueTransformer);
        original.put(Integer.valueOf(2), Integer.valueOf(20));

        Map additionalEntries = new LinkedHashMap();
        additionalEntries.put(Integer.valueOf(3), Integer.valueOf(30));
        original.putAll(additionalEntries);

        assertThat(original)
                .containsEntry("1", "10")
                .containsEntry("2", "20")
                .containsEntry("3", "30");

        byte[] serialized = serialize(original);
        Map restored = deserializeMap(serialized);

        assertThat(restored).isNotSameAs(original).isInstanceOf(TransformedMap.class);
        assertThat(restored)
                .hasSize(3)
                .containsEntry("1", "10")
                .containsEntry("2", "20")
                .containsEntry("3", "30");

        restored.put(Integer.valueOf(4), new StringBuilder("forty"));
        assertThat(restored).containsEntry("4", "forty");

        Map.Entry entry = findEntry(restored, "2");
        assertThat(entry.setValue(Integer.valueOf(200))).isEqualTo("20");
        assertThat(restored).containsEntry("2", "200");
    }

    private static byte[] serialize(Map map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static Map deserializeMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(Map.class);
            return (Map) restored;
        }
    }

    private static Map.Entry findEntry(Map map, Object key) {
        for (Object entryObject : map.entrySet()) {
            Map.Entry entry = (Map.Entry) entryObject;
            if (key.equals(entry.getKey())) {
                return entry;
            }
        }
        throw new AssertionError("No map entry found for key: " + key);
    }
}
