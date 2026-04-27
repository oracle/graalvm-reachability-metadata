/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class LinkedBlockingQueueTest {
    @Test
    void returnsTypedArrayWhenDestinationIsTooSmall() {
        LinkedBlockingQueue queue = new LinkedBlockingQueue();
        queue.add("first");
        queue.add("second");

        String[] contents = (String[]) queue.toArray(new String[0]);

        assertThat(contents).containsExactly("first", "second");
        assertThat(contents).isInstanceOf(String[].class);
    }

    @Test
    void serializesAndDeserializesElementsInQueueOrder() throws Exception {
        LinkedBlockingQueue original = new LinkedBlockingQueue(4);
        original.add("first");
        original.add("second");

        LinkedBlockingQueue restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(2);
        assertThat(restored.remainingCapacity()).isEqualTo(2);
        assertThat(restored.poll()).isEqualTo("first");
        assertThat(restored.poll()).isEqualTo("second");
        assertThat(restored.poll()).isNull();
    }

    private static byte[] serialize(LinkedBlockingQueue queue) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(queue);
        }
        return bytes.toByteArray();
    }

    private static LinkedBlockingQueue deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (LinkedBlockingQueue) input.readObject();
        }
    }
}
