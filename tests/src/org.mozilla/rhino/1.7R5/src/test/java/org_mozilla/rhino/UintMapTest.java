/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.UintMap;

public class UintMapTest {

    @Test
    void preservesObjectAndIntValuesAcrossSerialization() throws Exception {
        final UintMap map = new UintMap(2);
        map.put(3, "three");
        map.put(7, 49);
        map.put(11, "removed");
        map.put(19, 361);
        map.remove(11);

        final UintMap restored = deserialize(serialize(map));

        assertThat(restored.size()).isEqualTo(3);
        assertThat(restored.has(3)).isTrue();
        assertThat(restored.has(7)).isTrue();
        assertThat(restored.has(19)).isTrue();
        assertThat(restored.has(11)).isFalse();
        assertThat(restored.getObject(3)).isEqualTo("three");
        assertThat(restored.getObject(7)).isNull();
        assertThat(restored.getInt(3, -1)).isEqualTo(0);
        assertThat(restored.getInt(7, -1)).isEqualTo(49);
        assertThat(restored.getInt(19, -1)).isEqualTo(361);
        assertThat(restored.getInt(11, -1)).isEqualTo(-1);
        assertThat(restored.getKeys()).containsExactlyInAnyOrder(3, 7, 19);
    }

    private static byte[] serialize(final UintMap map) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }

        return outputStream.toByteArray();
    }

    private static UintMap deserialize(final byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (UintMap) objectInputStream.readObject();
        }
    }
}
