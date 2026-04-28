/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package plexus.plexus_utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.codehaus.plexus.util.FastMap;
import org.junit.jupiter.api.Test;

public class FastMapTest {
    @Test
    void serializationRoundTripPreservesEntriesAndCapacity() throws Exception {
        FastMap original = new FastMap(4);
        original.put("first-key", "first-value");
        original.put("second-key", Integer.valueOf(42));

        byte[] serialized = serialize(original);
        FastMap restored = (FastMap) deserialize(serialized);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.size()).isEqualTo(original.size());
        assertThat(restored.capacity()).isEqualTo(original.capacity());
        assertThat(restored.get("first-key")).isEqualTo("first-value");
        assertThat(restored.get("second-key")).isEqualTo(Integer.valueOf(42));
    }

    private static byte[] serialize(Object value) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
            output.flush();
            return bytes.toByteArray();
        }
    }

    private static Object deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return input.readObject();
        }
    }
}
