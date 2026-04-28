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
import org.mozilla.javascript.ObjToIntMap;

public class ObjToIntMapTest {

    @Test
    void preservesEntriesAcrossSerialization() throws Exception {
        ObjToIntMap map = new ObjToIntMap(2);
        map.put("alpha", 7);
        map.put("removed", 13);
        map.put("beta", 23);
        map.remove("removed");

        ObjToIntMap restored = deserialize(serialize(map));

        assertThat(restored.size()).isEqualTo(2);
        assertThat(restored.has("alpha")).isTrue();
        assertThat(restored.has("beta")).isTrue();
        assertThat(restored.has("removed")).isFalse();
        assertThat(restored.get("alpha", -1)).isEqualTo(7);
        assertThat(restored.get("beta", -1)).isEqualTo(23);
        assertThat(restored.get("missing", -1)).isEqualTo(-1);
        assertThat(restored.getKeys()).containsExactlyInAnyOrder("alpha", "beta");
    }

    private static byte[] serialize(ObjToIntMap map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }

        return outputStream.toByteArray();
    }

    private static ObjToIntMap deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ObjToIntMap) objectInputStream.readObject();
        }
    }
}
