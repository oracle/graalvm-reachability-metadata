/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.ArrayDeque;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayDequeTest {
    @Test
    void typedArrayConversionAllocatesArrayWithComponentTypeAndDequeOrder() {
        ArrayDeque deque = new ArrayDeque();
        deque.addLast("alpha");
        deque.addLast("bravo");
        deque.addFirst("zero");

        String[] values = (String[]) deque.toArray(new String[0]);

        assertThat(values).containsExactly("zero", "alpha", "bravo");
    }

    @Test
    void serializationRoundTripPreservesOrderAndDequeBehavior() throws Exception {
        ArrayDeque original = new ArrayDeque();
        original.addLast("first");
        original.addLast(Integer.valueOf(2));
        original.addLast(Boolean.TRUE);

        ArrayDeque restored = roundTrip(original);

        assertThat(restored.size()).isEqualTo(3);
        assertThat(restored.removeFirst()).isEqualTo("first");
        assertThat(restored.removeFirst()).isEqualTo(Integer.valueOf(2));
        assertThat(restored.removeFirst()).isEqualTo(Boolean.TRUE);
        restored.addFirst("new-first");
        restored.addLast("new-last");
        assertThat(restored.removeFirst()).isEqualTo("new-first");
        assertThat(restored.removeLast()).isEqualTo("new-last");
    }

    private static ArrayDeque roundTrip(ArrayDeque value) throws IOException, ClassNotFoundException {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialize(value)))) {
            return (ArrayDeque) inputStream.readObject();
        }
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }
}
