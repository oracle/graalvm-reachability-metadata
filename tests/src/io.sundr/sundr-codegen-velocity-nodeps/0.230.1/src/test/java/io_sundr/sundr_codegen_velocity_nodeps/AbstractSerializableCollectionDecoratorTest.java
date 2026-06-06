/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.collection.UnmodifiableCollection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import org.junit.jupiter.api.Test;

public class AbstractSerializableCollectionDecoratorTest {

    @Test
    public void serializesAndDeserializesUnmodifiableCollectionDecorator()
            throws IOException, ClassNotFoundException {
        Collection original = UnmodifiableCollection.decorate(new ArrayList(Arrays.asList("alpha", "beta", "gamma")));

        byte[] serialized = serialize(original);
        Collection restored = deserializeCollection(serialized);

        assertThat(restored).isNotSameAs(original).isInstanceOf(UnmodifiableCollection.class);
        assertThat(restored).containsExactly("alpha", "beta", "gamma");
        assertThat(restored.contains("beta")).isTrue();
        assertThat(restored.toArray()).containsExactly("alpha", "beta", "gamma");
        assertThatThrownBy(() -> restored.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);

        Iterator iterator = restored.iterator();
        assertThat(iterator.next()).isEqualTo("alpha");
        assertThatThrownBy(iterator::remove)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(Collection collection) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(collection);
        }
        return bytes.toByteArray();
    }

    private static Collection deserializeCollection(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(Collection.class);
            return (Collection) restored;
        }
    }
}
