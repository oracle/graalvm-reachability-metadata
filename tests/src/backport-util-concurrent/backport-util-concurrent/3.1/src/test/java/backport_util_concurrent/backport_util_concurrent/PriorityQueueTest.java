/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.PriorityQueue;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class PriorityQueueTest {
    @Test
    void copiesElementsIntoNewObjectArray() {
        PriorityQueue queue = createQueue();

        Object[] contents = queue.toArray();

        assertThat(contents).hasSize(queue.size());
        assertThat(contents).containsExactlyInAnyOrder(Integer.valueOf(1), Integer.valueOf(3), Integer.valueOf(4),
                Integer.valueOf(7), Integer.valueOf(9));
    }

    @Test
    void serializesAndDeserializesHeapContents() throws Exception {
        PriorityQueue original = createQueue();

        PriorityQueue restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(original.size());
        assertThat(restored.poll()).isEqualTo(Integer.valueOf(1));
        assertThat(restored.poll()).isEqualTo(Integer.valueOf(3));
        assertThat(restored.poll()).isEqualTo(Integer.valueOf(4));
        assertThat(restored.poll()).isEqualTo(Integer.valueOf(7));
        assertThat(restored.poll()).isEqualTo(Integer.valueOf(9));
        assertThat(restored).isEmpty();
    }

    private static PriorityQueue createQueue() {
        PriorityQueue queue = new PriorityQueue(2);
        queue.offer(Integer.valueOf(7));
        queue.offer(Integer.valueOf(1));
        queue.offer(Integer.valueOf(9));
        queue.offer(Integer.valueOf(3));
        queue.offer(Integer.valueOf(4));
        return queue;
    }

    private static byte[] serialize(PriorityQueue queue) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(queue);
        }
        return bytes.toByteArray();
    }

    private static PriorityQueue deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (PriorityQueue) input.readObject();
        }
    }
}
