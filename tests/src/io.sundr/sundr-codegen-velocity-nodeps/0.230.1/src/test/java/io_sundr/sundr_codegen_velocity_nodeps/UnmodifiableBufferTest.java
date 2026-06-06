/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.Buffer;
import io.sundr.deps.org.apache.commons.collections.buffer.BoundedFifoBuffer;
import io.sundr.deps.org.apache.commons.collections.buffer.UnmodifiableBuffer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Iterator;
import org.junit.jupiter.api.Test;

public class UnmodifiableBufferTest {

    @Test
    public void serializesAndDeserializesDecoratedBufferWithoutAllowingMutation()
            throws IOException, ClassNotFoundException {
        BoundedFifoBuffer decorated = new BoundedFifoBuffer(4);
        decorated.add("alpha");
        decorated.add("beta");
        decorated.add("gamma");
        Buffer original = UnmodifiableBuffer.decorate(decorated);

        byte[] serialized = serialize(original);
        Buffer restored = deserializeBuffer(serialized);

        assertThat(restored).isNotSameAs(original).isInstanceOf(UnmodifiableBuffer.class);
        assertThat(restored).containsExactly("alpha", "beta", "gamma");
        assertThat(restored.get()).isEqualTo("alpha");
        assertThatThrownBy(() -> restored.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(restored::remove)
                .isInstanceOf(UnsupportedOperationException.class);

        Iterator iterator = restored.iterator();
        assertThat(iterator.next()).isEqualTo("alpha");
        assertThatThrownBy(iterator::remove)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(Buffer buffer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(buffer);
        }
        return bytes.toByteArray();
    }

    private static Buffer deserializeBuffer(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(Buffer.class);
            return (Buffer) restored;
        }
    }
}
