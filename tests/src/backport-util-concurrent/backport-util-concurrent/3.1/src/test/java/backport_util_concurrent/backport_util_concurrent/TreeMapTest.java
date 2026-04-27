/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.TreeMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeMapTest {
    @Test
    void serializesAndDeserializesSortedEntries() throws Exception {
        TreeMap original = createTreeMap();

        TreeMap restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(original.size());
        assertThat(restored.comparator()).isNull();
        assertThat(restored.keySet().toArray()).containsExactly("alpha", "bravo", "charlie", "delta");
        assertThat(restored.get("alpha")).isEqualTo("one");
        assertThat(restored.get("bravo")).isEqualTo("two");
        assertThat(restored.get("charlie")).isEqualTo("three");
        assertThat(restored.get("delta")).isEqualTo("four");
        assertThat(restored.firstKey()).isEqualTo("alpha");
        assertThat(restored.lastKey()).isEqualTo("delta");
    }

    private static TreeMap createTreeMap() {
        TreeMap map = new TreeMap();
        map.put("delta", "four");
        map.put("alpha", "one");
        map.put("charlie", "three");
        map.put("bravo", "two");
        return map;
    }

    private static byte[] serialize(TreeMap map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static TreeMap deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (TreeMap) input.readObject();
        }
    }
}
