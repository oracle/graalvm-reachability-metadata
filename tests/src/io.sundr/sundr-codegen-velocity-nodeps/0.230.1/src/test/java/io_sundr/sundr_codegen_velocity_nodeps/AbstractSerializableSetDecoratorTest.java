/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.set.UnmodifiableSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class AbstractSerializableSetDecoratorTest {

    @Test
    public void serializesAndDeserializesUnmodifiableSetDecorator()
            throws IOException, ClassNotFoundException {
        Set decorated = new LinkedHashSet(Arrays.asList("alpha", "beta", "gamma"));
        Set original = UnmodifiableSet.decorate(decorated);

        byte[] serialized = serialize(original);
        Set restored = deserializeSet(serialized);

        assertThat(restored).isNotSameAs(original).isInstanceOf(UnmodifiableSet.class);
        assertThat(restored).containsExactly("alpha", "beta", "gamma");
        assertThat(restored.contains("beta")).isTrue();
        assertThat(restored.toArray()).containsExactly("alpha", "beta", "gamma");
        assertThatThrownBy(() -> restored.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(restored::clear)
                .isInstanceOf(UnsupportedOperationException.class);

        Iterator iterator = restored.iterator();
        assertThat(iterator.next()).isEqualTo("alpha");
        assertThatThrownBy(iterator::remove)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(Set set) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(set);
        }
        return bytes.toByteArray();
    }

    private static Set deserializeSet(byte[] serialized) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(Set.class);
            return (Set) restored;
        }
    }
}
