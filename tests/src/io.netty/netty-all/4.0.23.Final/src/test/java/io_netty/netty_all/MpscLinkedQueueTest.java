/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

public class MpscLinkedQueueTest {
    @Test
    void convertsToTypedArrayLargerThanInputArray() {
        Queue<String> queue = PlatformDependent.newMpscQueue();
        queue.add("alpha");
        queue.add("bravo");
        queue.add("charlie");

        String[] values = queue.toArray(new String[0]);

        assertThat(values).containsExactly("alpha", "bravo", "charlie");
    }

    @Test
    void serializesAndDeserializesQueuedElements() throws Exception {
        Queue<String> queue = PlatformDependent.newMpscQueue();
        queue.add("first");
        queue.add("second");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(queue);
        }

        Object deserialized;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            deserialized = input.readObject();
        }

        assertThat(deserialized).isInstanceOf(Queue.class);
        @SuppressWarnings("unchecked")
        Queue<String> restored = (Queue<String>) deserialized;
        assertThat(restored).containsExactly("first", "second");
        assertThat(restored.poll()).isEqualTo("first");
        assertThat(restored.poll()).isEqualTo("second");
        assertThat(restored).isEmpty();
    }
}
