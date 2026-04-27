/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.LinkedBlockingDeque;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class LinkedBlockingDequeTest {
    @Test
    void returnsTypedArrayWhenDestinationIsTooSmall() {
        LinkedBlockingDeque deque = new LinkedBlockingDeque();
        deque.addLast("front");
        deque.addLast("middle");
        deque.addLast("back");

        String[] contents = (String[]) deque.toArray(new String[0]);

        assertThat(contents).containsExactly("front", "middle", "back");
        assertThat(contents).isInstanceOf(String[].class);
    }

    @Test
    void serializesAndDeserializesElementsInDequeOrder() throws Exception {
        LinkedBlockingDeque original = new LinkedBlockingDeque(5);
        original.addLast("middle");
        original.addFirst("front");
        original.addLast("back");

        LinkedBlockingDeque restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(3);
        assertThat(restored.remainingCapacity()).isEqualTo(2);
        assertThat(restored.pollFirst()).isEqualTo("front");
        assertThat(restored.pollFirst()).isEqualTo("middle");
        assertThat(restored.pollFirst()).isEqualTo("back");
        assertThat(restored.pollFirst()).isNull();
    }

    private static byte[] serialize(LinkedBlockingDeque deque) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(deque);
        }
        return bytes.toByteArray();
    }

    private static LinkedBlockingDeque deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (LinkedBlockingDeque) input.readObject();
        }
    }
}
