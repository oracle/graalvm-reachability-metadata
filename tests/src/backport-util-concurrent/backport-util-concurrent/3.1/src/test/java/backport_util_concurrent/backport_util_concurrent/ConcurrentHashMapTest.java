/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentHashMapTest {
    @Test
    void serializesAndDeserializesEntries() throws Exception {
        ConcurrentHashMap original = new ConcurrentHashMap();
        original.put("primary", "alpha");
        original.put("secondary", "beta");
        original.put(Integer.valueOf(42), Long.valueOf(123456789L));

        ConcurrentHashMap restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(original.size());
        assertThat(restored.get("primary")).isEqualTo("alpha");
        assertThat(restored.get("secondary")).isEqualTo("beta");
        assertThat(restored.get(Integer.valueOf(42))).isEqualTo(Long.valueOf(123456789L));
    }

    private static byte[] serialize(ConcurrentHashMap map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static ConcurrentHashMap deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ConcurrentHashMap) input.readObject();
        }
    }
}
