/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.set.UnmodifiableSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractSerializableSetDecoratorTest {

    @Test
    void serializesAndDeserializesDecoratedSetState() throws Exception {
        Set<String> original = UnmodifiableSet.unmodifiableSet(new LinkedHashSet<>(List.of("alpha", "beta", "gamma")));

        assertThat(original).isInstanceOf(UnmodifiableSet.class);
        assertThatThrownBy(() -> original.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] serialized = serialize(original);
        Set<String> restored = deserializeUnmodifiableSet(serialized);

        assertThat(restored)
                .isInstanceOf(UnmodifiableSet.class)
                .containsExactlyInAnyOrder("alpha", "beta", "gamma");
        assertThatThrownBy(() -> restored.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(Set<String> set) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(set);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static Set<String> deserializeUnmodifiableSet(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(UnmodifiableSet.class);
            return (Set<String>) restored;
        }
    }
}
