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

import io.netty.util.internal.chmv8.ConcurrentHashMapV8;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentHashMapV8Test {
    @Test
    public void serializesAndDeserializesEntries() throws IOException, ClassNotFoundException {
        ConcurrentHashMapV8<String, String> map = new ConcurrentHashMapV8<>();
        map.put("alpha", "one");
        map.put("beta", "two");

        Object restoredObject = deserialize(serialize(map));

        assertThat(restoredObject).isInstanceOf(ConcurrentHashMapV8.class);
        ConcurrentHashMapV8<?, ?> restored = (ConcurrentHashMapV8<?, ?>) restoredObject;
        assertThat(restored).hasSize(2);
        assertThat(restored.get("alpha")).isEqualTo("one");
        assertThat(restored.get("beta")).isEqualTo("two");
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }
}
