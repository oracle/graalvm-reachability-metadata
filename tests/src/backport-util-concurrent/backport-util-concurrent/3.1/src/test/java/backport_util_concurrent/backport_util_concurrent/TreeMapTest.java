/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.TreeMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeMapTest {
    @Test
    void serializationRoundTripPreservesComparatorKeysAndValues() throws Exception {
        TreeMap original = new TreeMap(Collections.reverseOrder());
        original.put("bravo", "second");
        original.put("alpha", "first");
        original.put("charlie", "third");

        TreeMap restored = roundTrip(original);

        assertThat(restored.comparator()).isNotNull();
        assertThat(restored.size()).isEqualTo(3);
        assertThat(restored.keySet().toArray()).containsExactly("charlie", "bravo", "alpha");
        assertThat(restored.values().toArray()).containsExactly("third", "second", "first");
        assertThat(restored.get("alpha")).isEqualTo("first");
        assertThat(restored.put("delta", "fourth")).isNull();
        assertThat(restored.keySet().toArray()).containsExactly("delta", "charlie", "bravo", "alpha");
    }

    private static TreeMap roundTrip(TreeMap value) throws IOException, ClassNotFoundException {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialize(value)))) {
            return (TreeMap) inputStream.readObject();
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
