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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.immutables.value.internal.$guava$.base.$Supplier;
import org.immutables.value.internal.$guava$.collect.$ListMultimap;
import org.immutables.value.internal.$guava$.collect.$Multimaps;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MultimapsCustomListMultimapTest {

    @Test
    void customListMultimapRestoresFactoryAndEntriesAcrossRoundTrip() throws Exception {
        $ListMultimap<String, String> original = $Multimaps.newListMultimap(
                new LinkedHashMap<>(),
                new SerializableArrayListSupplier<>()
        );
        original.put("team", "ada");
        original.put("team", "grace");
        original.put("language", "java");

        assertThat(original.getClass().getName())
                .isEqualTo("org.immutables.value.internal.$guava$.collect.$Multimaps$CustomListMultimap");

        $ListMultimap<String, String> restored = roundTrip((Serializable) original, $ListMultimap.class);

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

    private static final class SerializableArrayListSupplier<T> implements $Supplier<List<T>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public List<T> get() {
            return new ArrayList<>();
        }
    }
}
