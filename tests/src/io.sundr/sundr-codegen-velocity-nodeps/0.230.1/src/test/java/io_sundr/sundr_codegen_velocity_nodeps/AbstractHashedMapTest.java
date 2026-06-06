/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.map.HashedMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class AbstractHashedMapTest {

    @Test
    public void serializesPopulatedHashedMapUsingItsPublicSerializationForm()
            throws IOException, ClassNotFoundException {
        HashedMap original = new HashedMap();
        original.put("alpha", "one");
        original.put("beta", "two");

        byte[] serialized = serialize(original);
        HashedMap restored = deserialize(serialized);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(2);
        assertThat(restored.get("alpha")).isEqualTo("one");
        assertThat(restored.get("beta")).isEqualTo("two");
    }

    private static byte[] serialize(HashedMap map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static HashedMap deserialize(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            return (HashedMap) inputStream.readObject();
        }
    }
}
