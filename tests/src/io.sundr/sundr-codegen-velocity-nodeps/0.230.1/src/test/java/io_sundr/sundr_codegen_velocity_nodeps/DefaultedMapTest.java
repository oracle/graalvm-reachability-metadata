/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.map.DefaultedMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class DefaultedMapTest {

    @Test
    public void serializesDecoratedMapStateAndDefaultLookupBehavior()
            throws IOException, ClassNotFoundException {
        DefaultedMap original = new DefaultedMap("fallback");
        original.put("present", "value");
        original.put("nullable", null);

        assertThat(original.get("missing")).isEqualTo("fallback");
        assertThat(original).doesNotContainKey("missing");

        byte[] serialized = serialize(original);
        DefaultedMap restored = deserialize(serialized);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(2);
        assertThat(restored.get("present")).isEqualTo("value");
        assertThat(restored.get("nullable")).isNull();
        assertThat(restored.get("missing")).isEqualTo("fallback");
        assertThat(restored).doesNotContainKey("missing");
    }

    private static byte[] serialize(DefaultedMap map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static DefaultedMap deserialize(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(DefaultedMap.class);
            return (DefaultedMap) restored;
        }
    }
}
