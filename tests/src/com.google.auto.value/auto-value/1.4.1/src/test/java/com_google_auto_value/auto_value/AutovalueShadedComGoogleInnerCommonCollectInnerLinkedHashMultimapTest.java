/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import autovalue.shaded.com.google$.common.collect.$LinkedHashMultimap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.AbstractMap.SimpleImmutableEntry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AutovalueShadedComGoogleInnerCommonCollectInnerLinkedHashMultimapTest {
    @Test
    void serializationPreservesDistinctKeysValuesAndIterationOrder() throws Exception {
        $LinkedHashMultimap<String, String> original = $LinkedHashMultimap.create();
        original.put("team", "ada");
        original.put("team", "grace");
        original.put("language", "java");
        original.put("team", "ada");

        $LinkedHashMultimap<String, String> restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.keySet()).containsExactly("team", "language");
        assertThat(restored.get("team")).containsExactly("ada", "grace");
        assertThat(restored.get("language")).containsExactly("java");
        assertThat(restored.entries()).containsExactly(
                new SimpleImmutableEntry<>("team", "ada"),
                new SimpleImmutableEntry<>("team", "grace"),
                new SimpleImmutableEntry<>("language", "java")
        );
    }

    private static $LinkedHashMultimap<String, String> roundTrip(
            $LinkedHashMultimap<String, String> value
    ) throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf($LinkedHashMultimap.class);
            @SuppressWarnings("unchecked")
            $LinkedHashMultimap<String, String> typedRestored = ($LinkedHashMultimap<String, String>) restored;
            return typedRestored;
        }
    }

    private static byte[] serialize($LinkedHashMultimap<String, String> value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }
}
