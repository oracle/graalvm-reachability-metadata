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
import java.util.LinkedList;
import java.util.Queue;

import org.apache.commons.collections4.queue.UnmodifiableQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnmodifiableQueueTest {

    @Test
    void serializesAndDeserializesUnmodifiableQueueDecorator() throws Exception {
        LinkedList<String> delegate = new LinkedList<>();
        delegate.add("alpha");
        delegate.add("beta");
        delegate.add("gamma");

        Queue<String> original = UnmodifiableQueue.unmodifiableQueue(delegate);

        assertThat(original)
                .isInstanceOf(UnmodifiableQueue.class)
                .containsExactly("alpha", "beta", "gamma");
        assertThat(original.peek()).isEqualTo("alpha");
        assertThatThrownBy(() -> original.offer("delta"))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] serialized = serialize(original);
        Queue<String> restored = deserializeQueue(serialized);

        assertThat(restored)
                .isInstanceOf(UnmodifiableQueue.class)
                .containsExactly("alpha", "beta", "gamma");
        assertThat(restored.peek()).isEqualTo("alpha");
        assertThatThrownBy(() -> restored.offer("delta"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(restored::poll)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(restored::remove)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.iterator().remove())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(Queue<String> queue) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(queue);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static Queue<String> deserializeQueue(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(UnmodifiableQueue.class);
            return (Queue<String>) restored;
        }
    }
}
