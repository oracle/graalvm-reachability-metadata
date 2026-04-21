/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CircularFifoQueueTest {

    @Test
    void serializesAndDeserializesPartiallyFilledQueue() throws Exception {
        CircularFifoQueue<String> original = new CircularFifoQueue<>(4);
        original.add("alpha");
        original.add("beta");
        original.add("gamma");

        CircularFifoQueue<String> restored = roundTrip(original);

        assertThat(restored).containsExactly("alpha", "beta", "gamma");
        assertThat(restored.maxSize()).isEqualTo(4);
        assertThat(restored.isAtFullCapacity()).isFalse();
        assertThat(restored.peek()).isEqualTo("alpha");

        restored.add("delta");

        assertThat(restored).containsExactly("alpha", "beta", "gamma", "delta");
        assertThat(restored.isAtFullCapacity()).isTrue();
    }

    @Test
    void serializesAndDeserializesWrappedQueueAtFullCapacity() throws Exception {
        CircularFifoQueue<String> original = new CircularFifoQueue<>(3);
        original.add("alpha");
        original.add("beta");
        original.add("gamma");
        original.add("delta");

        CircularFifoQueue<String> restored = roundTrip(original);

        assertThat(restored).containsExactly("beta", "gamma", "delta");
        assertThat(restored.maxSize()).isEqualTo(3);
        assertThat(restored.isAtFullCapacity()).isTrue();
        assertThat(restored.peek()).isEqualTo("beta");

        restored.add("epsilon");

        assertThat(restored).containsExactly("gamma", "delta", "epsilon");
        assertThat(restored.peek()).isEqualTo("gamma");
    }

    private static CircularFifoQueue<String> roundTrip(CircularFifoQueue<String> queue)
            throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(queue);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(CircularFifoQueue.class);
            return castQueue(restored);
        }
    }

    private static byte[] serialize(CircularFifoQueue<String> queue) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(queue);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static CircularFifoQueue<String> castQueue(Object queue) {
        return (CircularFifoQueue<String>) queue;
    }
}
