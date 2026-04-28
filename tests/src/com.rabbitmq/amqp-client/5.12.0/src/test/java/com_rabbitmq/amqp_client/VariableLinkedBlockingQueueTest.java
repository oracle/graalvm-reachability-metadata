/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq.amqp_client;

import com.rabbitmq.client.impl.VariableLinkedBlockingQueue;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class VariableLinkedBlockingQueueTest {
    @Test
    void typedToArrayAllocatesArrayForQueueElements() {
        VariableLinkedBlockingQueue<String> queue = new VariableLinkedBlockingQueue<>();
        queue.add("first");
        queue.add("second");

        CharSequence[] values = queue.toArray(new CharSequence[0]);

        assertThat(values).containsExactly("first", "second");
        assertThat(values).isInstanceOf(CharSequence[].class);
    }

    @Test
    void serializationRoundTripPreservesElementsAndCapacity() throws Exception {
        VariableLinkedBlockingQueue<String> queue = new VariableLinkedBlockingQueue<>(5);
        queue.add("first");
        queue.add("second");

        byte[] serialized = serialize(queue);

        VariableLinkedBlockingQueue<?> restored = deserialize(serialized);
        assertThat(restored.toArray()).containsExactly("first", "second");
        assertThat(restored.remainingCapacity()).isEqualTo(3);
    }

    private static byte[] serialize(VariableLinkedBlockingQueue<?> queue) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(queue);
        }
        return bytes.toByteArray();
    }

    private static VariableLinkedBlockingQueue<?> deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (VariableLinkedBlockingQueue<?>) input.readObject();
        }
    }
}
