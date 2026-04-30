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

import org.immutables.value.internal.$guava$.collect.$ImmutableListMultimap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ImmutableListMultimapTest {

    @Test
    void immutableListMultimapRestoresKeysValuesAndOrderAcrossRoundTrip() throws Exception {
        $ImmutableListMultimap<String, String> original = $ImmutableListMultimap.<String, String>builder()
                .put("team", "ada")
                .put("team", "grace")
                .put("language", "java")
                .put("language", "kotlin")
                .build();

        $ImmutableListMultimap<String, String> restored = roundTrip(original, $ImmutableListMultimap.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.get("team")).containsExactly("ada", "grace");
        assertThat(restored.get("language")).containsExactly("java", "kotlin");
        assertThat(restored.inverse().get("ada")).containsExactly("team");
        assertThat(restored.inverse().get("java")).containsExactly("language");
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
}
