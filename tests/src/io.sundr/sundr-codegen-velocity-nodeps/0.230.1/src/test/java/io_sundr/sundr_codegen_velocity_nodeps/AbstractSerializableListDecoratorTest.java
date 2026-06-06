/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.list.UnmodifiableList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import org.junit.jupiter.api.Test;

public class AbstractSerializableListDecoratorTest {

    @Test
    public void serializesAndDeserializesUnmodifiableListDecorator()
            throws IOException, ClassNotFoundException {
        List original = UnmodifiableList.decorate(
                new ArrayList(Arrays.asList("alpha", "beta", "gamma")));

        byte[] serialized = serialize(original);
        List restored = deserializeList(serialized);

        assertThat(restored).isNotSameAs(original).isInstanceOf(UnmodifiableList.class);
        assertThat(restored).containsExactly("alpha", "beta", "gamma");
        assertThat(restored.get(1)).isEqualTo("beta");
        assertThat(restored.indexOf("gamma")).isEqualTo(2);
        assertThat(restored.subList(0, 2)).containsExactly("alpha", "beta");
        assertThatThrownBy(() -> restored.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.set(0, "delta"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.remove(0))
                .isInstanceOf(UnsupportedOperationException.class);

        ListIterator iterator = restored.listIterator();
        assertThat(iterator.next()).isEqualTo("alpha");
        assertThatThrownBy(() -> iterator.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(List list) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(list);
        }
        return bytes.toByteArray();
    }

    private static List deserializeList(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(List.class);
            return (List) restored;
        }
    }
}
