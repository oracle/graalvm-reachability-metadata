/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.TreeSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeSetTest {
    @Test
    void serializationRoundTripPreservesComparatorAndElements() throws Exception {
        TreeSet original = new TreeSet(Collections.reverseOrder());
        original.add("bravo");
        original.add("alpha");
        original.add("charlie");

        TreeSet restored = roundTrip(original);

        assertThat(restored.comparator()).isNotNull();
        assertThat(restored.size()).isEqualTo(3);
        assertThat(restored.toArray()).containsExactly("charlie", "bravo", "alpha");
        assertThat(restored.first()).isEqualTo("charlie");
        assertThat(restored.last()).isEqualTo("alpha");
        assertThat(restored.contains("bravo")).isTrue();
        assertThat(restored.add("delta")).isTrue();
        assertThat(restored.toArray()).containsExactly("delta", "charlie", "bravo", "alpha");
    }

    private static TreeSet roundTrip(TreeSet value) throws IOException, ClassNotFoundException {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialize(value)))) {
            return (TreeSet) inputStream.readObject();
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
