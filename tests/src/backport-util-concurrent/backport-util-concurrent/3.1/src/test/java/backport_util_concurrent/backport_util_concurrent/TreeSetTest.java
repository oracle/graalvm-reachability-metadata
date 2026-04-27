/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.TreeSet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeSetTest {
    @Test
    void serializesAndDeserializesSortedElements() throws Exception {
        TreeSet original = createTreeSet();

        TreeSet restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(original.size());
        assertThat(restored.comparator()).isNull();
        assertThat(restored.toArray()).containsExactly("alpha", "bravo", "charlie", "delta");
        assertThat(restored.first()).isEqualTo("alpha");
        assertThat(restored.last()).isEqualTo("delta");
    }

    private static TreeSet createTreeSet() {
        TreeSet set = new TreeSet();
        set.add("delta");
        set.add("alpha");
        set.add("charlie");
        set.add("bravo");
        return set;
    }

    private static byte[] serialize(TreeSet set) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(set);
        }
        return bytes.toByteArray();
    }

    private static TreeSet deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (TreeSet) input.readObject();
        }
    }
}
