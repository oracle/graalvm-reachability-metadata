/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.CursorableLinkedList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class CursorableLinkedListTest {

    @Test
    public void toArrayCreatesRequestedRuntimeArrayTypeWhenSuppliedArrayIsTooSmall() {
        CursorableLinkedList list = new CursorableLinkedList();
        list.add("alpha");
        list.add("beta");

        Object[] result = list.toArray(new CharSequence[0]);

        assertThat(result).isExactlyInstanceOf(CharSequence[].class);
        assertThat(result).containsExactly("alpha", "beta");
    }

    @Test
    public void serializesPopulatedListUsingItsPublicSerializationForm()
            throws IOException, ClassNotFoundException {
        CursorableLinkedList original = new CursorableLinkedList();
        original.add("alpha");
        original.add("beta");
        original.add("gamma");

        byte[] serialized = serialize(original);
        CursorableLinkedList restored = deserialize(serialized);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).containsExactly("alpha", "beta", "gamma");
    }

    private static byte[] serialize(CursorableLinkedList list) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(list);
        }
        return bytes.toByteArray();
    }

    private static CursorableLinkedList deserialize(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            return (CursorableLinkedList) inputStream.readObject();
        }
    }
}
