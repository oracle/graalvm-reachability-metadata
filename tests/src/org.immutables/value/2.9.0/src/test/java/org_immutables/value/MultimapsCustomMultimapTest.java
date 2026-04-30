/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_immutables.value;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashMap;

import org.immutables.value.internal.$guava$.base.$Supplier;
import org.immutables.value.internal.$guava$.collect.$Multimap;
import org.immutables.value.internal.$guava$.collect.$Multimaps;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MultimapsCustomMultimapTest {

    @Test
    void customMultimapRestoresFactoryAndEntriesAcrossRoundTrip() throws Exception {
        $Multimap<String, String> original = $Multimaps.newMultimap(
                new LinkedHashMap<>(),
                new SerializableArrayDequeSupplier<>()
        );
        original.put("team", "ada");
        original.put("team", "grace");
        original.put("language", "java");

        assertThat(original.getClass().getName())
                .isEqualTo("org.immutables.value.internal.$guava$.collect.$Multimaps$CustomMultimap");

        $Multimap<String, String> restored = roundTrip((Serializable) original, $Multimap.class);

        assertThat(restored.get("team")).containsExactly("ada", "grace");
        assertThat(restored.get("language")).containsExactly("java");
        assertThat(restored.keySet()).containsExactly("team", "language");

        restored.put("team", "margaret");
        restored.put("database", "postgres");

        assertThat(restored.get("team")).containsExactly("ada", "grace", "margaret");
        assertThat(restored.get("database")).containsExactly("postgres");
        assertThat(restored.keySet()).containsExactly("team", "language", "database");
    }

    private static <T> T roundTrip(Serializable value, Class<T> expectedType) throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(expectedType);
            return expectedType.cast(restored);
        }
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }

    private static final class SerializableArrayDequeSupplier<T> implements $Supplier<Collection<T>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Collection<T> get() {
            return new ArrayDeque<>();
        }
    }
}
