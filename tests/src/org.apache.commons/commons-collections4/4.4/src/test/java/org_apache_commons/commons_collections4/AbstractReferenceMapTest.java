/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.map.AbstractReferenceMap.ReferenceStrength;
import org.apache.commons.collections4.map.ReferenceMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractReferenceMapTest {

    @Test
    void serializesAndDeserializesReferenceMapEntries() throws Exception {
        ReferenceMap<String, String> original = new ReferenceMap<>(ReferenceStrength.HARD, ReferenceStrength.HARD, 1, 0.75f, true);
        original.put("alpha", "one");

        byte[] serialized = serialize(original);
        ReferenceMap<String, String> restored = deserializeReferenceMap(serialized);

        assertThat(restored)
                .hasSize(1)
                .containsEntry("alpha", "one");

        restored.put("beta", "two");

        assertThat(restored)
                .hasSize(2)
                .containsEntry("alpha", "one")
                .containsEntry("beta", "two");
    }

    private static byte[] serialize(ReferenceMap<String, String> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static ReferenceMap<String, String> deserializeReferenceMap(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(ReferenceMap.class);
            return (ReferenceMap<String, String>) restored;
        }
    }
}
