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

import org.apache.commons.collections4.map.DefaultedMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultedMapTest {

    @Test
    void serializesAndDeserializesDecoratedMapStateAndDefaultLookupBehavior() throws Exception {
        DefaultedMap<String, String> original = new DefaultedMap<>("fallback");
        original.put("present", "value");
        original.put("nullable", null);

        assertThat(original.get("missing")).isEqualTo("fallback");
        assertThat(original).doesNotContainKey("missing");

        byte[] serialized = serialize(original);
        DefaultedMap<String, String> restored = deserializeDefaultedMap(serialized);

        assertThat(restored)
                .hasSize(2)
                .containsEntry("present", "value")
                .containsEntry("nullable", null);
        assertThat(restored.get("missing")).isEqualTo("fallback");
        assertThat(restored).doesNotContainKey("missing");
        assertThat(restored.get("nullable")).isNull();
    }

    private static byte[] serialize(DefaultedMap<String, String> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static DefaultedMap<String, String> deserializeDefaultedMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(DefaultedMap.class);
            return (DefaultedMap<String, String>) restored;
        }
    }
}
