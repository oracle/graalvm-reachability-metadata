/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.LinkedBlockingDeque;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LinkedBlockingDequeTest {
    @Test
    void typedArrayConversionAllocatesArrayWithComponentTypeAndDequeOrder() {
        LinkedBlockingDeque deque = new LinkedBlockingDeque(4);
        deque.addLast("alpha");
        deque.addLast("bravo");
        deque.addFirst("zero");

        String[] values = (String[]) deque.toArray(new String[0]);

        assertThat(values).containsExactly("zero", "alpha", "bravo");
    }

    @Test
    void serializationRoundTripPreservesCapacityOrderAndDequeBehavior() throws Exception {
        LinkedBlockingDeque original = new LinkedBlockingDeque(4);
        original.addLast("first");
        original.addLast(Integer.valueOf(2));
        original.addFirst("zero");

        LinkedBlockingDeque restored = roundTrip(original);

        assertThat(restored.size()).isEqualTo(3);
        assertThat(restored.remainingCapacity()).isEqualTo(1);
        assertThat(restored.removeFirst()).isEqualTo("zero");
        assertThat(restored.removeLast()).isEqualTo(Integer.valueOf(2));
        assertThat(restored.removeFirst()).isEqualTo("first");
        assertThat(restored.offerLast("new-last")).isTrue();
        assertThat(restored.offerFirst("new-first")).isTrue();
        assertThat(restored.removeFirst()).isEqualTo("new-first");
        assertThat(restored.removeLast()).isEqualTo("new-last");
    }

    private static LinkedBlockingDeque roundTrip(LinkedBlockingDeque value)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialize(value)))) {
            return (LinkedBlockingDeque) inputStream.readObject();
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
