/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Queue;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MpscLinkedQueueTest {
    @Test
    public void createsTypedArrayForQueueContents() {
        Queue<String> queue = PlatformDependent.newMpscQueue();
        queue.add("alpha");
        queue.add("beta");

        String[] contents = queue.toArray(new String[0]);

        assertThat(contents).containsExactly("alpha", "beta");
        assertThat(queue).containsExactly("alpha", "beta");
    }

    @Test
    public void serializesAndDeserializesQueueContents() throws IOException, ClassNotFoundException {
        Queue<String> queue = PlatformDependent.newMpscQueue();
        queue.add("alpha");
        queue.add("beta");

        Object restoredObject = deserialize(serialize(queue));

        assertThat(restoredObject).isInstanceOf(Queue.class);
        Queue<?> restored = (Queue<?>) restoredObject;
        assertThat(restored.poll()).isEqualTo("alpha");
        assertThat(restored.poll()).isEqualTo("beta");
        assertThat(restored).isEmpty();
        assertThat(queue).containsExactly("alpha", "beta");
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }
}
