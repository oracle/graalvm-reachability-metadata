/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.BidiMap;
import io.sundr.deps.org.apache.commons.collections.bidimap.DualHashBidiMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class DualHashBidiMapTest {

    @Test
    public void serializesPopulatedDualHashBidiMapUsingItsPublicSerializationForm()
            throws IOException, ClassNotFoundException {
        DualHashBidiMap original = new DualHashBidiMap();
        original.put("alpha", "one");
        original.put("beta", "two");

        byte[] serialized = serialize(original);
        DualHashBidiMap restored = deserialize(serialized);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(2);
        assertThat(restored.get("alpha")).isEqualTo("one");
        assertThat(restored.get("beta")).isEqualTo("two");
        assertThat(restored.getKey("one")).isEqualTo("alpha");
        assertThat(restored.getKey("two")).isEqualTo("beta");

        BidiMap inverse = restored.inverseBidiMap();
        assertThat(inverse.get("one")).isEqualTo("alpha");
        assertThat(inverse.get("two")).isEqualTo("beta");
    }

    private static byte[] serialize(DualHashBidiMap map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static DualHashBidiMap deserialize(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            return (DualHashBidiMap) inputStream.readObject();
        }
    }
}
