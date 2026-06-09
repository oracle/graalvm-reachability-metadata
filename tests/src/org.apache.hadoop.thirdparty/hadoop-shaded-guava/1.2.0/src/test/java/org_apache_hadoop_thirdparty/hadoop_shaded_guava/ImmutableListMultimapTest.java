/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.hadoop.thirdparty.com.google.common.collect.ImmutableListMultimap;
import org.junit.jupiter.api.Test;

public class ImmutableListMultimapTest {
    @Test
    void roundTripSerializesDistinctKeysAndOrderedValues() throws Exception {
        ImmutableListMultimap<String, String> original = ImmutableListMultimap.<String, String>builder()
                .put("letters", "a")
                .put("letters", "b")
                .put("numbers", "one")
                .build();

        ImmutableListMultimap<String, String> restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.keySet()).containsExactly("letters", "numbers");
        assertThat(restored.get("letters")).containsExactly("a", "b");
        assertThat(restored.get("numbers")).containsExactly("one");
    }

    private static ImmutableListMultimap<String, String> roundTrip(
            ImmutableListMultimap<String, String> value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(ImmutableListMultimap.class);
            @SuppressWarnings("unchecked")
            ImmutableListMultimap<String, String> typedRestored =
                    (ImmutableListMultimap<String, String>) restored;
            return typedRestored;
        }
    }
}
