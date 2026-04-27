/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentLinkedQueueTest {
    @Test
    void serializesAndDeserializesElementsInFifoOrder() throws Exception {
        ConcurrentLinkedQueue original = new ConcurrentLinkedQueue();
        original.offer("first");
        original.offer("second");
        original.offer("third");

        ConcurrentLinkedQueue restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(3);
        assertThat(restored.poll()).isEqualTo("first");
        assertThat(restored.poll()).isEqualTo("second");
        assertThat(restored.poll()).isEqualTo("third");
        assertThat(restored.poll()).isNull();
        assertThat(restored).isEmpty();
    }

    private static byte[] serialize(ConcurrentLinkedQueue queue) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(queue);
        }
        return bytes.toByteArray();
    }

    private static ConcurrentLinkedQueue deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ConcurrentLinkedQueue) input.readObject();
        }
    }
}
