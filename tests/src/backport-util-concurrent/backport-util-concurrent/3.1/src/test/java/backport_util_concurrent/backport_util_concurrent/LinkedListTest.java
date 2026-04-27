/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.LinkedList;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class LinkedListTest {
    @Test
    void copiesElementsIntoNewTypedArrayWhenSuppliedArrayIsTooSmall() {
        LinkedList list = createList();

        String[] contents = (String[]) list.toArray(new String[0]);

        assertThat(contents).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void serializesAndDeserializesElementsInListOrder() throws Exception {
        LinkedList original = createList();

        LinkedList restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(original.size());
        assertThat(restored.removeFirst()).isEqualTo("alpha");
        assertThat(restored.removeFirst()).isEqualTo("beta");
        assertThat(restored.removeFirst()).isEqualTo("gamma");
        assertThat(restored).isEmpty();
    }

    private static LinkedList createList() {
        LinkedList list = new LinkedList();
        list.addLast("alpha");
        list.addLast("beta");
        list.addLast("gamma");
        return list;
    }

    private static byte[] serialize(LinkedList list) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(list);
        }
        return bytes.toByteArray();
    }

    private static LinkedList deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (LinkedList) input.readObject();
        }
    }
}
