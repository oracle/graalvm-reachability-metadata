/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.map.Flat3Map;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Flat3MapTest {

    @Test
    public void serializesPopulatedFlat3MapUsingItsPublicSerializationForm()
            throws IOException, ClassNotFoundException {
        Flat3Map original = new Flat3Map();
        original.put("alpha", "one");
        original.put("beta", "two");
        original.put("gamma", "three");

        byte[] serialized = serialize(original);
        Flat3Map restored = deserialize(serialized);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(3);
        assertThat((Map) restored)
                .containsEntry("alpha", "one")
                .containsEntry("beta", "two")
                .containsEntry("gamma", "three");
    }

    private static byte[] serialize(Flat3Map map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static Flat3Map deserialize(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            return (Flat3Map) inputStream.readObject();
        }
    }
}
