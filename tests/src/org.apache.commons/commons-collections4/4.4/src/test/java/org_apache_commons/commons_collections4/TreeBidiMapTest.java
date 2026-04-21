/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.bidimap.TreeBidiMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeBidiMapTest {

    @Test
    void serializesAndDeserializesBidirectionalEntries() throws Exception {
        TreeBidiMap<String, String> original = new TreeBidiMap<>();
        original.put("alpha", "one");
        original.put("beta", "two");

        byte[] serialized = serialize(original);
        TreeBidiMap<String, String> restored = deserializeTreeBidiMap(serialized);

        assertThat(restored)
                .hasSize(2)
                .containsEntry("alpha", "one")
                .containsEntry("beta", "two");
        assertThat(restored.getKey("one")).isEqualTo("alpha");
        assertThat(restored.getKey("two")).isEqualTo("beta");
        assertThat(restored.firstKey()).isEqualTo("alpha");
        assertThat(restored.nextKey("alpha")).isEqualTo("beta");
        assertThat(restored.inverseBidiMap())
                .hasSize(2)
                .containsEntry("one", "alpha")
                .containsEntry("two", "beta");
    }

    private static byte[] serialize(TreeBidiMap<String, String> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static TreeBidiMap<String, String> deserializeTreeBidiMap(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(TreeBidiMap.class);
            return (TreeBidiMap<String, String>) restored;
        }
    }
}
