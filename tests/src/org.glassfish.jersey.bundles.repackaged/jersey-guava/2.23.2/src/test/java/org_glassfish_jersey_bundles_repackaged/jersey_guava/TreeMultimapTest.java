/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_bundles_repackaged.jersey_guava;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import jersey.repackaged.com.google.common.collect.Ordering;
import jersey.repackaged.com.google.common.collect.TreeMultimap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeMultimapTest {
    @Test
    void serializationRoundTripPreservesComparatorsAndEntries() throws Exception {
        TreeMultimap<String, Integer> original = TreeMultimap.create(
                Ordering.<String>natural().reverse(), Ordering.<Integer>natural().reverse());
        original.put("alpha", 1);
        original.put("alpha", 3);
        original.put("beta", 2);

        TreeMultimap<String, Integer> restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.keySet()).containsExactly("beta", "alpha");
        assertThat(restored.get("alpha")).containsExactly(3, 1);
        assertThat(restored.get("beta")).containsExactly(2);
        assertThat(restored.keyComparator().compare("alpha", "beta")).isGreaterThan(0);
        assertThat(restored.valueComparator().compare(1, 2)).isGreaterThan(0);
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static TreeMultimap<String, Integer> roundTrip(TreeMultimap<String, Integer> value)
            throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(TreeMultimap.class);
            return (TreeMultimap<String, Integer>) restored;
        }
    }
}
