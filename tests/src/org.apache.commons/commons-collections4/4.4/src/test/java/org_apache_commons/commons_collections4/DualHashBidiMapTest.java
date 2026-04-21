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

import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DualHashBidiMapTest {

    @Test
    void serializesAndDeserializesBidirectionalEntries() throws Exception {
        DualHashBidiMap<String, String> original = new DualHashBidiMap<>();
        original.put("alpha", "one");
        original.put("beta", "two");
        original.put("gamma", "three");

        byte[] serialized = serialize(original);
        DualHashBidiMap<String, String> restored = deserializeDualHashBidiMap(serialized);

        assertThat(restored)
                .hasSize(3)
                .containsEntry("alpha", "one")
                .containsEntry("beta", "two")
                .containsEntry("gamma", "three");
        assertThat(restored.getKey("one")).isEqualTo("alpha");
        assertThat(restored.getKey("two")).isEqualTo("beta");
        assertThat(restored.getKey("three")).isEqualTo("gamma");

        assertThat(restored.inverseBidiMap())
                .hasSize(3)
                .containsEntry("one", "alpha")
                .containsEntry("two", "beta")
                .containsEntry("three", "gamma");

        restored.inverseBidiMap().put("four", "delta");

        assertThat(restored)
                .hasSize(4)
                .containsEntry("delta", "four");
        assertThat(restored.getKey("four")).isEqualTo("delta");
    }

    private static byte[] serialize(DualHashBidiMap<String, String> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static DualHashBidiMap<String, String> deserializeDualHashBidiMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(DualHashBidiMap.class);
            return (DualHashBidiMap<String, String>) restored;
        }
    }
}
