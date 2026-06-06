/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.buffer.UnboundedFifoBuffer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class UnboundedFifoBufferTest {

    @Test
    public void serializesExpandedBufferUsingItsPublicSerializationForm()
            throws IOException, ClassNotFoundException {
        UnboundedFifoBuffer original = new UnboundedFifoBuffer(2);
        original.add("alpha");
        original.add("beta");
        assertThat(original.remove()).isEqualTo("alpha");
        original.add("gamma");
        original.add("delta");

        byte[] serialized = serialize(original);
        UnboundedFifoBuffer restored = deserialize(serialized);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).containsExactly("beta", "gamma", "delta");
        assertThat(restored.remove()).isEqualTo("beta");
        assertThat(restored.remove()).isEqualTo("gamma");
        assertThat(restored.remove()).isEqualTo("delta");
        assertThat(restored).isEmpty();
    }

    private static byte[] serialize(UnboundedFifoBuffer buffer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(buffer);
        }
        return bytes.toByteArray();
    }

    private static UnboundedFifoBuffer deserialize(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(UnboundedFifoBuffer.class);
            return (UnboundedFifoBuffer) restored;
        }
    }
}
