/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MultiKeyMapTest {

    @Test
    void serializesAndDeserializesEntriesAcrossSupportedKeyArity() throws Exception {
        MultiKeyMap<String, String> original = new MultiKeyMap<>();
        original.put("alpha", "beta", "two-keys");
        original.put("alpha", "beta", "gamma", "three-keys");
        original.put("alpha", "beta", "gamma", "delta", "four-keys");
        original.put("alpha", "beta", "gamma", "delta", "epsilon", "five-keys");
        original.put(new MultiKey<>("direct", "entry"), "map-api");

        byte[] serialized = serialize(original);
        MultiKeyMap<String, String> restored = deserializeMultiKeyMap(serialized);

        assertThat(restored)
                .hasSize(5)
                .containsEntry(new MultiKey<>("direct", "entry"), "map-api");
        assertThat(restored.get("alpha", "beta")).isEqualTo("two-keys");
        assertThat(restored.get("alpha", "beta", "gamma")).isEqualTo("three-keys");
        assertThat(restored.get("alpha", "beta", "gamma", "delta")).isEqualTo("four-keys");
        assertThat(restored.get("alpha", "beta", "gamma", "delta", "epsilon")).isEqualTo("five-keys");
        assertThat(restored.containsKey("alpha", "beta", "gamma", "delta", "epsilon")).isTrue();

        assertThat(restored.removeMultiKey("alpha", "beta", "gamma", "delta", "epsilon"))
                .isEqualTo("five-keys");
        restored.put("zeta", "eta", "replacement");

        assertThat(restored)
                .hasSize(5)
                .doesNotContainKey(new MultiKey<>("alpha", "beta", "gamma", "delta", "epsilon"))
                .containsEntry(new MultiKey<>("zeta", "eta"), "replacement")
                .containsEntry(new MultiKey<>("direct", "entry"), "map-api");
        assertThat(restored.get("zeta", "eta")).isEqualTo("replacement");
    }

    private static byte[] serialize(MultiKeyMap<String, String> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static MultiKeyMap<String, String> deserializeMultiKeyMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(MultiKeyMap.class);
            return (MultiKeyMap<String, String>) restored;
        }
    }
}
