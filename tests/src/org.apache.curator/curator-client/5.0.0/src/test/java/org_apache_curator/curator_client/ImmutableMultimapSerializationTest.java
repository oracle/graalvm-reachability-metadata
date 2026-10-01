/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.curator.shaded.com.google.common.collect.ImmutableListMultimap;
import org.apache.curator.shaded.com.google.common.collect.ImmutableSetMultimap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ImmutableMultimapSerializationTest {
    @Test
    void preservesImmutableMultimapsWhenRoundTripped() throws Exception {
        ImmutableListMultimap<String, Integer> list = ImmutableListMultimap.of("key", 1, "key", 2);
        ImmutableSetMultimap<String, Integer> set = ImmutableSetMultimap.of("key", 1, "key", 2);

        assertThat(roundTrip(list)).isEqualTo(list);
        assertThat(roundTrip(set)).isEqualTo(set);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
