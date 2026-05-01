/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.LinkedList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LinkedListTest {
    @Test
    void typedArrayConversionAllocatesArrayWithComponentTypeAndListOrder() {
        LinkedList list = new LinkedList();
        list.add("alpha");
        list.add("bravo");
        list.addFirst("zero");

        String[] values = (String[]) list.toArray(new String[0]);

        assertThat(values).containsExactly("zero", "alpha", "bravo");
        assertThat(values.getClass().getComponentType()).isEqualTo(String.class);
    }

    @Test
    void serializationRoundTripPreservesElementsAndDequeBehavior() throws Exception {
        LinkedList original = new LinkedList();
        original.addLast("first");
        original.addLast(Integer.valueOf(2));
        original.addLast(null);
        original.addLast(Boolean.TRUE);

        LinkedList restored = roundTrip(original);

        assertThat(restored.size()).isEqualTo(4);
        assertThat(restored.removeFirst()).isEqualTo("first");
        assertThat(restored.removeFirst()).isEqualTo(Integer.valueOf(2));
        assertThat(restored.removeFirst()).isNull();
        assertThat(restored.removeFirst()).isEqualTo(Boolean.TRUE);
        assertThat(restored.isEmpty()).isTrue();

        restored.addFirst("new-first");
        restored.addLast("new-last");
        assertThat(restored.removeFirst()).isEqualTo("new-first");
        assertThat(restored.removeLast()).isEqualTo("new-last");
    }

    private static LinkedList roundTrip(LinkedList value) throws IOException, ClassNotFoundException {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialize(value)))) {
            return (LinkedList) inputStream.readObject();
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
