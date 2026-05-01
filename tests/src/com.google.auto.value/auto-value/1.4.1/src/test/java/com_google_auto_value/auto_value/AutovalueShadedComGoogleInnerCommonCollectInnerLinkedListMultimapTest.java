/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import autovalue.shaded.com.google$.common.collect.$LinkedListMultimap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.AbstractMap.SimpleImmutableEntry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AutovalueShadedComGoogleInnerCommonCollectInnerLinkedListMultimapTest {
    @Test
    void serializationPreservesDuplicateValuesAndGlobalEntryOrder() throws Exception {
        $LinkedListMultimap<String, String> original = $LinkedListMultimap.create();
        original.put("team", "ada");
        original.put("language", "java");
        original.put("team", "ada");
        original.put("team", "grace");

        $LinkedListMultimap<String, String> restored = roundTrip(original);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).isEqualTo(original);
        assertThat(restored.keySet()).containsExactly("team", "language");
        assertThat(restored.get("team")).containsExactly("ada", "ada", "grace");
        assertThat(restored.get("language")).containsExactly("java");
        assertThat(restored.values()).containsExactly("ada", "java", "ada", "grace");
        assertThat(restored.entries()).containsExactly(
                new SimpleImmutableEntry<>("team", "ada"),
                new SimpleImmutableEntry<>("language", "java"),
                new SimpleImmutableEntry<>("team", "ada"),
                new SimpleImmutableEntry<>("team", "grace")
        );
    }

    private static $LinkedListMultimap<String, String> roundTrip(
            $LinkedListMultimap<String, String> value
    ) throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf($LinkedListMultimap.class);
            @SuppressWarnings("unchecked")
            $LinkedListMultimap<String, String> typedRestored = ($LinkedListMultimap<String, String>) restored;
            return typedRestored;
        }
    }

    private static byte[] serialize($LinkedListMultimap<String, String> value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }
}
