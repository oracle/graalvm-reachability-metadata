/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentLinkedQueue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentLinkedQueueTest {
    @Test
    void serializationRoundTripPreservesFifoOrderAndQueueOperations() throws Exception {
        ConcurrentLinkedQueue original = new ConcurrentLinkedQueue();
        original.add("alpha");
        original.add("bravo");
        original.add("charlie");

        ConcurrentLinkedQueue restored = roundTrip(original);

        assertThat(restored).hasSize(3);
        assertThat(restored.poll()).isEqualTo("alpha");
        assertThat(restored.poll()).isEqualTo("bravo");
        assertThat(restored.offer("delta")).isTrue();
        assertThat(restored.poll()).isEqualTo("charlie");
        assertThat(restored.poll()).isEqualTo("delta");
        assertThat(restored.poll()).isNull();
    }

    private static ConcurrentLinkedQueue roundTrip(ConcurrentLinkedQueue value)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialize(value)))) {
            return (ConcurrentLinkedQueue) inputStream.readObject();
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
