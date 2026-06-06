/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.Transformer;
import io.sundr.deps.org.apache.commons.collections.functors.ConstantTransformer;
import io.sundr.deps.org.apache.commons.collections.map.LazyMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class LazyMapTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void serializesDecoratedMapAndPreservesLazyFactoryBehavior()
            throws IOException, ClassNotFoundException {
        Map backingMap = new HashMap();
        backingMap.put("present", "value");
        Transformer transformer = ConstantTransformer.getInstance("created");
        Map original = LazyMap.decorate(backingMap, transformer);

        assertThat(original.get("missing-before-serialization")).isEqualTo("created");
        assertThat(original).containsEntry("missing-before-serialization", "created");

        byte[] serialized = serialize(original);
        Map restored = deserialize(serialized);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).containsEntry("present", "value");
        assertThat(restored).containsEntry("missing-before-serialization", "created");
        assertThat(restored.get("missing-after-serialization")).isEqualTo("created");
        assertThat(restored).containsEntry("missing-after-serialization", "created");
    }

    private static byte[] serialize(Map map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static Map deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(LazyMap.class);
            return (Map) restored;
        }
    }
}
