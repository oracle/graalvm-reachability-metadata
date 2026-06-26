/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package plexus.plexus_utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import org.codehaus.plexus.util.FastMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FastMapTest {
    @Test
    void serializesAndDeserializesEntriesUsingFastMapCustomSerialization() throws Exception {
        FastMap original = new FastMap(4);
        original.put("alpha", "one");
        original.put("beta", "two");

        byte[] serialized = serialize(original);

        Object deserialized = deserialize(serialized);

        assertThat(deserialized).isInstanceOf(FastMap.class).isNotSameAs(original);
        FastMap restored = (FastMap) deserialized;
        assertThat(restored.capacity()).isEqualTo(original.capacity());
        assertThat(restored.size()).isEqualTo(2);
        assertThat(restored.get("alpha")).isEqualTo("one");
        assertThat(restored.get("beta")).isEqualTo("two");
        assertThat(restored.keySet()).containsExactlyElementsOf(Arrays.asList("alpha", "beta"));
        assertThat(restored.values()).containsExactlyElementsOf(Arrays.asList("one", "two"));
    }

    private static byte[] serialize(FastMap map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
            stream.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return stream.readObject();
        }
    }
}
