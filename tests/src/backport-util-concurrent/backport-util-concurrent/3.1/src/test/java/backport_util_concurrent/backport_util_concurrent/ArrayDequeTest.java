/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayDequeTest {
    @Test
    void copiesElementsIntoNewTypedArray() {
        ArrayDeque deque = createWrappedDeque();

        String[] contents = (String[]) deque.toArray(new String[0]);

        assertThat(contents).containsExactly("three", "four", "five", "six", "seven", "eight");
    }

    @Test
    void serializesAndDeserializesElementsInDequeOrder() throws Exception {
        ArrayDeque original = createWrappedDeque();

        ArrayDeque restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(original.size());
        assertThat(restored.removeFirst()).isEqualTo("three");
        assertThat(restored.removeFirst()).isEqualTo("four");
        assertThat(restored.removeFirst()).isEqualTo("five");
        assertThat(restored.removeFirst()).isEqualTo("six");
        assertThat(restored.removeFirst()).isEqualTo("seven");
        assertThat(restored.removeFirst()).isEqualTo("eight");
        assertThat(restored).isEmpty();
    }

    private static ArrayDeque createWrappedDeque() {
        ArrayDeque deque = new ArrayDeque(4);
        deque.addLast("one");
        deque.addLast("two");
        deque.addLast("three");
        deque.addLast("four");
        deque.addLast("five");
        deque.addLast("six");
        deque.removeFirst();
        deque.removeFirst();
        deque.addLast("seven");
        deque.addLast("eight");
        return deque;
    }

    private static byte[] serialize(ArrayDeque deque) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(deque);
        }
        return bytes.toByteArray();
    }

    private static ArrayDeque deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ArrayDeque) input.readObject();
        }
    }
}
