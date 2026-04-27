/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentSkipListMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentSkipListMapTest {
    @Test
    void serializesAndDeserializesOrderedEntries() throws Exception {
        ConcurrentSkipListMap original = new ConcurrentSkipListMap();
        original.put("bravo", "second");
        original.put("alpha", "first");
        original.put("charlie", Integer.valueOf(3));

        ConcurrentSkipListMap restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(original.size());
        assertThat(restored.firstKey()).isEqualTo("alpha");
        assertThat(restored.lastKey()).isEqualTo("charlie");
        assertThat(restored.get("alpha")).isEqualTo("first");
        assertThat(restored.get("bravo")).isEqualTo("second");
        assertThat(restored.get("charlie")).isEqualTo(Integer.valueOf(3));
    }

    private static byte[] serialize(ConcurrentSkipListMap map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static ConcurrentSkipListMap deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ConcurrentSkipListMap) input.readObject();
        }
    }
}
