/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_bundles_repackaged.jersey_guava;

import static org.assertj.core.api.Assertions.assertThat;

import jersey.repackaged.com.google.common.collect.HashMultimap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

public class SerializationTest {
    @Test
    void hashMultimapSerializationRoundTripPreservesKeysAndValues() throws Exception {
        HashMultimap<String, String> original = HashMultimap.create();
        original.put("alpha", "one");
        original.put("alpha", "two");
        original.put("beta", "three");

        HashMultimap<String, String> deserialized = roundTrip(original);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.get("alpha")).containsExactlyInAnyOrder("one", "two");
        assertThat(deserialized.get("beta")).containsExactly("three");
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T original) throws Exception {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (T) input.readObject();
        }
    }
}
