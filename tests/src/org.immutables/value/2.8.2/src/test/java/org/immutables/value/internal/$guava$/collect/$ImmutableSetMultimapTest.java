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
import java.util.Comparator;
import org.junit.jupiter.api.Test;

class $ImmutableSetMultimapTest {
    @Test
    void serializesAndDeserializesEntriesWithSortedValues() throws Exception {
        final $ImmutableSetMultimap<String, Integer> source = $ImmutableSetMultimap.<String, Integer>builder()
                .orderValuesBy(Comparator.naturalOrder())
                .put("letters", 3)
                .put("letters", 1)
                .put("letters", 2)
                .put("numbers", 5)
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
        final $ImmutableSetMultimap<String, Integer> restored =
                ($ImmutableSetMultimap<String, Integer>) restoredObject;
        assertThat(restored.size()).isEqualTo(4);
        assertThat(restored.keySet()).containsExactly("letters", "numbers");
        assertThat(restored.get("letters")).containsExactly(1, 2, 3);
        assertThat(restored.get("numbers")).containsExactly(5);
        assertThat(restored.inverse().get(1)).containsExactly("letters");
        assertThat(restored.inverse().get(5)).containsExactly("numbers");
    }
}
