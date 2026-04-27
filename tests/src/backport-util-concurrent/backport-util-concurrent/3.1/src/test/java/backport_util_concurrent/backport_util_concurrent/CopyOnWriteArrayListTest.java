/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class CopyOnWriteArrayListTest {
    @Test
    void serializesAndDeserializesElementsFromArrayBackedList() throws Exception {
        Object[] source = {"first", "second", null};
        CopyOnWriteArrayList original = new CopyOnWriteArrayList(Arrays.asList(source));
        source[0] = "changed";
        original.add("third");

        CopyOnWriteArrayList restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(4);
        assertThat(restored.get(0)).isEqualTo("first");
        assertThat(restored.get(1)).isEqualTo("second");
        assertThat(restored.get(2)).isNull();
        assertThat(restored.get(3)).isEqualTo("third");
        assertThat(original.get(0)).isEqualTo("first");
    }

    private static byte[] serialize(CopyOnWriteArrayList list) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(list);
        }
        return bytes.toByteArray();
    }

    private static CopyOnWriteArrayList deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (CopyOnWriteArrayList) input.readObject();
        }
    }
}
