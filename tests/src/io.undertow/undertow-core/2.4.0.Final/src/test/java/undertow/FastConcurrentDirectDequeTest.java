/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import io.undertow.util.FastConcurrentDirectDeque;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class FastConcurrentDirectDequeTest {

    @Test
    void serializedDequePreservesElementOrder() throws Exception {
        FastConcurrentDirectDeque<String> deque = new FastConcurrentDirectDeque<>();
        deque.addLast("first");
        deque.addLast("second");

        byte[] serializedDeque = serialize(deque);
        FastConcurrentDirectDeque<String> deserializedDeque = deserialize(serializedDeque);

        assertThat(deserializedDeque).containsExactly("first", "second");
        assertThat(deserializedDeque.peekFirst()).isEqualTo("first");
        assertThat(deserializedDeque.peekLast()).isEqualTo("second");
    }

    private byte[] serialize(FastConcurrentDirectDeque<String> deque) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(deque);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private FastConcurrentDirectDeque<String> deserialize(byte[] serializedDeque) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedDeque))) {
            Object object = input.readObject();
            assertThat(object).isInstanceOf(FastConcurrentDirectDeque.class);
            return (FastConcurrentDirectDeque<String>) object;
        }
    }
}
