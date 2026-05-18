/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.BlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

public class LinkedTransferQueueTest {
    private static final String LINKED_TRANSFER_QUEUE_CLASS_NAME =
            "org.glassfish.grizzly.utils.LinkedTransferQueue";

    @Test
    void serializesAndDeserializesQueueElements() throws Exception {
        BlockingQueue<Object> queue = deserialize(serializedQueueWithTwoElements());

        assertThat(queue.getClass().getName()).isEqualTo(LINKED_TRANSFER_QUEUE_CLASS_NAME);
        assertThat(queue).containsExactly("first", "second");

        byte[] serialized = serialize(queue);
        BlockingQueue<Object> restored = deserialize(serialized);

        assertThat(restored.getClass().getName()).isEqualTo(LINKED_TRANSFER_QUEUE_CLASS_NAME);
        assertThat(restored).containsExactly("first", "second");
    }

    private static byte[] serializedQueueWithTwoElements() {
        return hexToBytes("""
                ac ed 00 05 73 72 00 2f 6f 72 67 2e 67 6c 61 73
                73 66 69 73 68 2e 67 72 69 7a 7a 6c 79 2e 75 74
                69 6c 73 2e 4c 69 6e 6b 65 64 54 72 61 6e 73 66
                65 72 51 75 65 75 65 d3 45 33 6e 1f 5c 3e 9a 03
                00 00 78 70 74 00 05 66 69 72 73 74 74 00 06 73
                65 63 6f 6e 64 70 78
                """);
    }

    private static byte[] serialize(BlockingQueue<?> queue) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(queue);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static BlockingQueue<Object> deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object value = input.readObject();
            assertThat(value).isInstanceOf(BlockingQueue.class);
            return (BlockingQueue<Object>) value;
        }
    }

    private static byte[] hexToBytes(String hex) {
        String normalized = hex.replaceAll("\\s+", "");
        assertThat(normalized.length() % 2).isZero();

        byte[] bytes = new byte[normalized.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int fromIndex = i * 2;
            bytes[i] = (byte) Integer.parseInt(normalized.substring(fromIndex, fromIndex + 2), 16);
        }
        return bytes;
    }
}
