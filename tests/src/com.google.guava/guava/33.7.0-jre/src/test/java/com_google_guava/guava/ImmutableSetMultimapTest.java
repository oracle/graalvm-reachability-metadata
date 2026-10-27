/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableSetMultimap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class ImmutableSetMultimapTest {
    @Test
    void roundTripSerializesDistinctKeysAndValues() throws Exception {
        ImmutableSetMultimap<String, String> multimap = ImmutableSetMultimap.<String, String>builder()
                .put("letters", "a")
                .put("letters", "b")
                .put("numbers", "one")
                .build();

        ImmutableSetMultimap<String, String> restored = roundTrip(multimap);

        assertThat(restored).isEqualTo(multimap);
        assertThat(restored.keySet()).containsExactly("letters", "numbers");
        assertThat(restored.get("letters")).containsExactly("a", "b");
        assertThat(restored.get("numbers")).containsExactly("one");
    }

    private static ImmutableSetMultimap<String, String> roundTrip(
            ImmutableSetMultimap<String, String> value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(ImmutableSetMultimap.class);
            @SuppressWarnings("unchecked")
            ImmutableSetMultimap<String, String> typedRestored =
                    (ImmutableSetMultimap<String, String>) restored;
            return typedRestored;
        }
    }
}
