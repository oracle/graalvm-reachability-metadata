/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Queue;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MpscLinkedQueueTest {
    @Test
    void toArrayWithUndersizedTypedArrayCreatesMatchingComponentArray() {
        Queue<String> queue = PlatformDependent.newMpscQueue();
        queue.add("alpha");
        queue.add("bravo");

        String[] values = queue.toArray(new String[0]);

        Assertions.assertArrayEquals(new String[] { "alpha", "bravo" }, values);
        Assertions.assertEquals(String.class, values.getClass().getComponentType());
    }

    @Test
    void serializationRoundTripPreservesQueuedElements() throws Exception {
        Queue<String> original = PlatformDependent.newMpscQueue();
        original.add("alpha");
        original.add("bravo");
        original.add("charlie");

        Queue<String> restored = deserialize(serialize(original));

        Assertions.assertArrayEquals(new String[] { "alpha", "bravo", "charlie" }, restored.toArray(new String[0]));
        Assertions.assertEquals(3, original.size());
    }

    private static byte[] serialize(Queue<String> queue) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(queue);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static Queue<String> deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (Queue<String>) in.readObject();
        }
    }
}
