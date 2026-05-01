/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.CopyOnWriteArrayList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CopyOnWriteArrayListTest {
    @Test
    void objectArrayConstructorCopiesInputAndPreservesListOrder() {
        Object[] values = new Object[] {"alpha", Integer.valueOf(2), Boolean.TRUE};

        CopyOnWriteArrayList list = new CopyOnWriteArrayList(values);
        values[0] = "changed";

        assertThat(list).containsExactly("alpha", Integer.valueOf(2), Boolean.TRUE);
        assertThat(list.toArray(new Object[0])).containsExactly("alpha", Integer.valueOf(2), Boolean.TRUE);
    }

    @Test
    void serializationRoundTripPreservesElementsAndCopyOnWriteBehavior() throws Exception {
        CopyOnWriteArrayList original = new CopyOnWriteArrayList(Arrays.asList("first", Integer.valueOf(2), null));

        CopyOnWriteArrayList restored = roundTrip(original);
        original.set(0, "changed");

        assertThat(restored).containsExactly("first", Integer.valueOf(2), null);
        assertThat(restored.addIfAbsent("third")).isTrue();
        assertThat(restored.addIfAbsent("third")).isFalse();
        assertThat(restored).containsExactly("first", Integer.valueOf(2), null, "third");
    }

    private static CopyOnWriteArrayList roundTrip(CopyOnWriteArrayList value) throws IOException, ClassNotFoundException {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialize(value)))) {
            return (CopyOnWriteArrayList) inputStream.readObject();
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
