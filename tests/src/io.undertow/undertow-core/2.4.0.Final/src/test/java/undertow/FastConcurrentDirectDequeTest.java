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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class FastConcurrentDirectDequeTest {

    @Test
    void serializesAndDeserializesElementsInDequeOrder() throws Exception {
        FastConcurrentDirectDeque<String> deque = new FastConcurrentDirectDeque<>(Arrays.asList("alpha", "bravo", "charlie"));
        deque.addFirst("first");
        deque.addLast("last");

        FastConcurrentDirectDeque<String> restored = deserialize(serialize(deque));

        assertThat(restored).containsExactly("first", "alpha", "bravo", "charlie", "last");
        assertThat(restored.pollFirst()).isEqualTo("first");
        assertThat(restored.pollLast()).isEqualTo("last");
        assertThat(restored).containsExactly("alpha", "bravo", "charlie");
    }

    private static byte[] serialize(FastConcurrentDirectDeque<String> deque) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(deque);
        }
        return bytes.toByteArray();
    }

    private static FastConcurrentDirectDeque<String> deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            @SuppressWarnings("unchecked")
            FastConcurrentDirectDeque<String> deque = (FastConcurrentDirectDeque<String>) inputStream.readObject();
            return deque;
        }
    }
}
