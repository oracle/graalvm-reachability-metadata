/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.thirdparty.com.google.common.collect.ImmutableSetMultimap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class SerializationInnerFieldSetterTest {
    @Test
    void immutableSetMultimapRestoresItsTransientMapAndSizeFields() throws Exception {
        ImmutableSetMultimap<String, String> original = ImmutableSetMultimap.<String, String>builder()
                .put("letters", "a")
                .put("letters", "b")
                .put("numbers", "one")
                .build();

        ImmutableSetMultimap<String, String> restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.size()).isEqualTo(3);
        assertThat(restored.get("letters")).containsExactly("a", "b");
    }

    private static ImmutableSetMultimap<String, String> roundTrip(
            ImmutableSetMultimap<String, String> value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Object restored = input.readObject();
            assertThat(restored).isInstanceOf(ImmutableSetMultimap.class);
            @SuppressWarnings("unchecked")
            ImmutableSetMultimap<String, String> typed =
                    (ImmutableSetMultimap<String, String>) restored;
            return typed;
        }
    }
}
