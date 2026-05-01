/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentHashMapTest {
    @Test
    void serializationRoundTripPreservesEntriesAndConcurrentMapBehavior() throws Exception {
        ConcurrentHashMap original = new ConcurrentHashMap(4);
        original.put("alpha", "one");
        original.put("beta", Integer.valueOf(2));
        original.put("gamma", Boolean.TRUE);

        ConcurrentHashMap restored = roundTrip(original);

        assertThat(restored.size()).isEqualTo(3);
        assertThat(restored.get("alpha")).isEqualTo("one");
        assertThat(restored.get("beta")).isEqualTo(Integer.valueOf(2));
        assertThat(restored.get("gamma")).isEqualTo(Boolean.TRUE);
        assertThat(restored.putIfAbsent("delta", "four")).isNull();
        assertThat(restored.replace("alpha", "one", "uno")).isTrue();
        assertThat(restored.get("alpha")).isEqualTo("uno");
    }

    private static ConcurrentHashMap roundTrip(ConcurrentHashMap value) throws IOException, ClassNotFoundException {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialize(value)))) {
            return (ConcurrentHashMap) inputStream.readObject();
        }
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }
}
