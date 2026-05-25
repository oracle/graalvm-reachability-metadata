/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import io.undertow.util.PortableConcurrentDirectDeque;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class PortableConcurrentDirectDequeTest {

    @Test
    void serializedDequePreservesElementOrder() throws Exception {
        PortableConcurrentDirectDeque<String> deque = new PortableConcurrentDirectDeque<>();
        deque.addLast("first");
        deque.addLast("second");

        byte[] serializedDeque = serialize(deque);
        PortableConcurrentDirectDeque<String> deserializedDeque = deserialize(serializedDeque);

        assertThat(deserializedDeque).containsExactly("first", "second");
        assertThat(deserializedDeque.peekFirst()).isEqualTo("first");
        assertThat(deserializedDeque.peekLast()).isEqualTo("second");
    }

    private byte[] serialize(PortableConcurrentDirectDeque<String> deque) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(deque);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private PortableConcurrentDirectDeque<String> deserialize(byte[] serializedDeque) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedDeque))) {
            Object object = input.readObject();
            assertThat(object).isInstanceOf(PortableConcurrentDirectDeque.class);
            return (PortableConcurrentDirectDeque<String>) object;
        }
    }
}
