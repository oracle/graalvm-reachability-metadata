/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import org.glassfish.grizzly.utils.DataStructures;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentHashMapV8Test {
    @Test
    void serializesAndDeserializesConcurrentMapEntries() throws Exception {
        ConcurrentMap<String, Integer> map = DataStructures.getConcurrentMap();
        map.put("first", 1);
        map.put("second", 2);

        assertThat(map.getClass().getName()).isEqualTo("org.glassfish.grizzly.utils.ConcurrentHashMapV8");

        byte[] serialized = serialize(map);

        ConcurrentMap<String, Integer> restored = deserialize(serialized);
        assertThat(restored.getClass().getName()).isEqualTo("org.glassfish.grizzly.utils.ConcurrentHashMapV8");
        assertThat(restored)
                .containsEntry("first", 1)
                .containsEntry("second", 2)
                .hasSize(2);
    }

    private static byte[] serialize(ConcurrentMap<String, Integer> map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentMap<String, Integer> deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (ConcurrentMap<String, Integer>) input.readObject();
        }
    }
}
