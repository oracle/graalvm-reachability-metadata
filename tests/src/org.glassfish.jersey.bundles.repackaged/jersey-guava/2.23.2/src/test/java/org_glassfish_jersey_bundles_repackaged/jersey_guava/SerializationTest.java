/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_bundles_repackaged.jersey_guava;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import jersey.repackaged.com.google.common.collect.ArrayListMultimap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializationTest {
    @Test
    void arrayListMultimapRoundTripPreservesDistinctKeysAndOrderedValues() throws Exception {
        ArrayListMultimap<String, String> original = ArrayListMultimap.create();
        original.put("language", "java");
        original.put("language", "native-image");
        original.put("runtime", "graalvm");

        ArrayListMultimap<String, String> restored = roundTrip(original, ArrayListMultimap.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.keySet()).containsExactlyInAnyOrder("language", "runtime");
        assertThat(restored.get("language")).containsExactly("java", "native-image");
        assertThat(restored.get("runtime")).containsExactly("graalvm");
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(Object value, Class<?> expectedType) throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(expectedType);
            return (T) restored;
        }
    }
}
