/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class AbstractBiMapInnerInverseTest {
    @Test
    void inverseViewRoundTripPreservesForwardAndBackwardMappings() throws Exception {
        HashBiMap<String, Integer> forward = HashBiMap.create();
        forward.put("alpha", 1);
        forward.put("beta", 2);

        BiMap<Integer, String> restoredInverse = deserialize(serialize(forward.inverse()));

        assertThat(restoredInverse).containsEntry(1, "alpha");
        assertThat(restoredInverse).containsEntry(2, "beta");
        assertThat(restoredInverse.inverse()).containsEntry("alpha", 1);
        assertThat(restoredInverse.inverse()).containsEntry("beta", 2);

        restoredInverse.put(3, "gamma");

        assertThat(restoredInverse.inverse()).containsEntry("gamma", 3);
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (T) input.readObject();
        }
    }
}
