/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.PriorityQueue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PriorityQueueTest {
    @Test
    void objectArrayConversionAllocatesObjectArrayAndSerializationPreservesPriorityOrder() throws Exception {
        PriorityQueue original = new PriorityQueue(2);
        original.offer("delta");
        original.offer("alpha");
        original.offer("charlie");
        original.offer("bravo");

        Object[] values = original.toArray();

        assertThat(values).containsExactlyInAnyOrder("alpha", "bravo", "charlie", "delta");
        assertThat(values.getClass().getComponentType()).isEqualTo(Object.class);

        PriorityQueue restored = roundTrip(original);
        restored.offer("aardvark");

        assertThat(restored.size()).isEqualTo(5);
        assertThat(restored.poll()).isEqualTo("aardvark");
        assertThat(restored.poll()).isEqualTo("alpha");
        assertThat(restored.poll()).isEqualTo("bravo");
        assertThat(restored.poll()).isEqualTo("charlie");
        assertThat(restored.poll()).isEqualTo("delta");
        assertThat(restored.poll()).isNull();
    }

    private static PriorityQueue roundTrip(PriorityQueue value) throws IOException, ClassNotFoundException {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialize(value)))) {
            return (PriorityQueue) inputStream.readObject();
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
