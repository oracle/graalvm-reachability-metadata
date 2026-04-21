/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.map.Flat3Map;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class Flat3MapTest {

    @Test
    void serializesAndDeserializesInlineEntries() throws Exception {
        Flat3Map<String, Integer> original = new Flat3Map<>();
        original.put("alpha", 1);
        original.put("beta", 2);
        original.put(null, 3);

        byte[] serialized = serialize(original);
        Flat3Map<String, Integer> restored = deserializeFlat3Map(serialized);

        assertThat(restored)
                .hasSize(3)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 2)
                .containsEntry(null, 3);

        restored.put("gamma", 4);

        assertThat(restored)
                .hasSize(4)
                .containsEntry("gamma", 4);
    }

    @Test
    void serializesAndDeserializesDelegateMapEntries() throws Exception {
        Flat3Map<String, Integer> original = new Flat3Map<>();
        original.put("alpha", 1);
        original.put("beta", 2);
        original.put("gamma", 3);
        original.put("delta", 4);

        byte[] serialized = serialize(original);
        Flat3Map<String, Integer> restored = deserializeFlat3Map(serialized);

        assertThat(restored)
                .hasSize(4)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 2)
                .containsEntry("gamma", 3)
                .containsEntry("delta", 4);

        restored.remove("beta");
        restored.put("epsilon", 5);

        assertThat(restored)
                .hasSize(4)
                .doesNotContainKey("beta")
                .containsEntry("epsilon", 5);
    }

    private static byte[] serialize(Flat3Map<String, Integer> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static Flat3Map<String, Integer> deserializeFlat3Map(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(Flat3Map.class);
            return (Flat3Map<String, Integer>) restored;
        }
    }
}
