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
import java.util.HashMap;

import org.apache.commons.collections4.FactoryUtils;
import org.apache.commons.collections4.map.LazyMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LazyMapTest {

    @Test
    void serializesAndDeserializesLazyMapStateAndFactoryBehavior() throws Exception {
        LazyMap<String, String> original = LazyMap.lazyMap(new HashMap<>(), FactoryUtils.constantFactory("generated"));
        original.put("present", "value");

        assertThat(original.get("present")).isEqualTo("value");
        assertThat(original).doesNotContainKey("missing");

        byte[] serialized = serialize(original);
        LazyMap<String, String> restored = deserializeLazyMap(serialized);

        assertThat(restored)
                .hasSize(1)
                .containsEntry("present", "value");
        assertThat(restored).doesNotContainKey("missing");
        assertThat(restored.get("missing")).isEqualTo("generated");
        assertThat(restored).containsEntry("missing", "generated");
    }

    private static byte[] serialize(LazyMap<String, String> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static LazyMap<String, String> deserializeLazyMap(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(LazyMap.class);
            return (LazyMap<String, String>) restored;
        }
    }
}
