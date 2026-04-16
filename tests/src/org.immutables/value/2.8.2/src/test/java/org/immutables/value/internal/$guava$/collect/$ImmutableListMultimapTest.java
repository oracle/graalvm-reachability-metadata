/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.immutables.value.internal.$guava$.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

class $ImmutableListMultimapTest {
    @Test
    void serializesAndDeserializesEntriesPreservingOrderAndDuplicates() throws Exception {
        final $ImmutableListMultimap<String, Integer> source = $ImmutableListMultimap.<String, Integer>builder()
                .put("letters", 1)
                .put("letters", 2)
                .put("letters", 2)
                .put("numbers", 3)
                .build();

        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(byteStream)) {
            outputStream.writeObject(source);
        }

        final Object restoredObject;
        try (ObjectInputStream inputStream = new ObjectInputStream(
                new ByteArrayInputStream(byteStream.toByteArray()))) {
            restoredObject = inputStream.readObject();
        }

        assertThat(restoredObject.getClass()).isEqualTo(source.getClass());

        @SuppressWarnings("unchecked")
        final $ImmutableListMultimap<String, Integer> restored =
                ($ImmutableListMultimap<String, Integer>) restoredObject;
        assertThat(restored.size()).isEqualTo(4);
        assertThat(restored.keySet()).containsExactly("letters", "numbers");
        assertThat(restored.get("letters")).containsExactly(1, 2, 2);
        assertThat(restored.get("numbers")).containsExactly(3);
        assertThat(restored.inverse().get(2)).containsExactly("letters", "letters");
        assertThat(restored.inverse().get(3)).containsExactly("numbers");
    }
}
