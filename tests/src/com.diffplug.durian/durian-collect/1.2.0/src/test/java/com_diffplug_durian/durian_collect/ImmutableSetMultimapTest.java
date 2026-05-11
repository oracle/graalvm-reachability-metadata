/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import com.diffplug.common.collect.ImmutableSetMultimap;

public class ImmutableSetMultimapTest {
    @Test
    void serializesKeysAndValues() throws Exception {
        ImmutableSetMultimap<String, String> original = ImmutableSetMultimap.<String, String> builder()
                .put("letters", "a")
                .put("letters", "b")
                .put("digits", "1")
                .build();

        ImmutableSetMultimap<String, String> copy = roundTrip(original);

        assertThat(copy.get("letters")).containsExactlyInAnyOrder("a", "b");
        assertThat(copy.get("digits")).containsExactly("1");
        assertThat(copy.keySet()).containsExactlyInAnyOrder("letters", "digits");
        assertThat(copy).isEqualTo(original);
    }

    private static <K, V> ImmutableSetMultimap<K, V> roundTrip(ImmutableSetMultimap<K, V> original)
            throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
            output.flush();
            serialized = bytes.toByteArray();
        }

        Object copy;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            copy = input.readObject();
        }

        assertThat(copy).isInstanceOf(ImmutableSetMultimap.class);
        @SuppressWarnings("unchecked")
        ImmutableSetMultimap<K, V> typedCopy = (ImmutableSetMultimap<K, V>) copy;
        return typedCopy;
    }
}
