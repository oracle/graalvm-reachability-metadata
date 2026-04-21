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
import java.util.ArrayList;

import org.apache.commons.collections4.bidimap.DualLinkedHashBidiMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DualLinkedHashBidiMapTest {

    @Test
    void serializesAndDeserializesEntriesWhilePreservingInsertionOrder() throws Exception {
        DualLinkedHashBidiMap<String, String> original = new DualLinkedHashBidiMap<>();
        original.put("alpha", "one");
        original.put("beta", "two");
        original.put("gamma", "three");

        byte[] serialized = serialize(original);
        DualLinkedHashBidiMap<String, String> restored = deserializeDualLinkedHashBidiMap(serialized);

        assertThat(restored)
                .hasSize(3)
                .containsEntry("alpha", "one")
                .containsEntry("beta", "two")
                .containsEntry("gamma", "three");
        assertThat(new ArrayList<>(restored.keySet())).containsExactly("alpha", "beta", "gamma");
        assertThat(new ArrayList<>(restored.values())).containsExactly("one", "two", "three");
        assertThat(restored.inverseBidiMap())
                .hasSize(3)
                .containsEntry("one", "alpha")
                .containsEntry("two", "beta")
                .containsEntry("three", "gamma");
        assertThat(new ArrayList<>(restored.inverseBidiMap().keySet())).containsExactly("one", "two", "three");

        restored.inverseBidiMap().put("four", "delta");

        assertThat(restored)
                .hasSize(4)
                .containsEntry("delta", "four");
        assertThat(restored.getKey("four")).isEqualTo("delta");
        assertThat(new ArrayList<>(restored.keySet())).containsExactly("alpha", "beta", "gamma", "delta");
    }

    private static byte[] serialize(DualLinkedHashBidiMap<String, String> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static DualLinkedHashBidiMap<String, String> deserializeDualLinkedHashBidiMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(DualLinkedHashBidiMap.class);
            return (DualLinkedHashBidiMap<String, String>) restored;
        }
    }
}
