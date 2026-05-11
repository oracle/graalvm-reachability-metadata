/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jsr166_mirror.jsr166y;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import jsr166y.LinkedTransferQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LinkedTransferQueueTest {
    @Test
    void serializesElementsAndNullSentinelThenDeserializesInOrder() throws Exception {
        LinkedTransferQueue<String> queue = new LinkedTransferQueue<>();
        queue.add("first");
        queue.add("second");

        byte[] serializedQueue = serialize(queue);

        LinkedTransferQueue<String> deserializedQueue = deserializeQueue(serializedQueue);
        assertThat(deserializedQueue).containsExactly("first", "second");
        assertThat(deserializedQueue.remainingCapacity()).isEqualTo(Integer.MAX_VALUE);
    }

    private static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(object);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static <E> LinkedTransferQueue<E> deserializeQueue(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (LinkedTransferQueue<E>) input.readObject();
        }
    }
}
