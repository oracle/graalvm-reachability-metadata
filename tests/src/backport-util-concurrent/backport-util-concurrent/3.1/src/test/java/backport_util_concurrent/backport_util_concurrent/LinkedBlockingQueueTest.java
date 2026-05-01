/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.LinkedBlockingQueue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LinkedBlockingQueueTest {
    @Test
    void typedArrayConversionAllocatesArrayWithComponentTypeAndQueueOrder() {
        LinkedBlockingQueue queue = new LinkedBlockingQueue(4);
        queue.add("alpha");
        queue.add("bravo");
        queue.add("charlie");

        String[] values = (String[]) queue.toArray(new String[0]);

        assertThat(values).containsExactly("alpha", "bravo", "charlie");
    }

    @Test
    void serializationRoundTripPreservesCapacityOrderAndBlockingQueueBehavior() throws Exception {
        LinkedBlockingQueue original = new LinkedBlockingQueue(4);
        original.add("first");
        original.add("second");

        LinkedBlockingQueue restored = roundTrip(original);

        assertThat(restored.size()).isEqualTo(2);
        assertThat(restored.remainingCapacity()).isEqualTo(2);
        assertThat(restored.take()).isEqualTo("first");
        assertThat(restored.take()).isEqualTo("second");
        assertThat(restored.offer("third")).isTrue();
        assertThat(restored.peek()).isEqualTo("third");
    }

    private static LinkedBlockingQueue roundTrip(LinkedBlockingQueue value)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialize(value)))) {
            return (LinkedBlockingQueue) inputStream.readObject();
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
