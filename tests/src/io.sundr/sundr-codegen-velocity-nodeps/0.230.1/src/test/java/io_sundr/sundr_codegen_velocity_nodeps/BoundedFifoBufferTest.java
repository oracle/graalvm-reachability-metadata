/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.buffer.BoundedFifoBuffer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class BoundedFifoBufferTest {

    @Test
    public void serializesPopulatedBufferUsingItsPublicSerializationForm()
            throws IOException, ClassNotFoundException {
        BoundedFifoBuffer original = new BoundedFifoBuffer(4);
        original.add("alpha");
        original.add("beta");
        original.add("gamma");

        byte[] serialized = serialize(original);
        BoundedFifoBuffer restored = deserialize(serialized);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.maxSize()).isEqualTo(4);
        assertThat(restored).containsExactly("alpha", "beta", "gamma");
        assertThat(restored.remove()).isEqualTo("alpha");
        assertThat(restored.remove()).isEqualTo("beta");
        assertThat(restored.remove()).isEqualTo("gamma");
    }

    private static byte[] serialize(BoundedFifoBuffer buffer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(buffer);
        }
        return bytes.toByteArray();
    }

    private static BoundedFifoBuffer deserialize(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            return (BoundedFifoBuffer) inputStream.readObject();
        }
    }
}
