/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.internal.util.MapMaker;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

public class CustomConcurrentHashMapInnerImplTest {
    @Test
    void serializesAndDeserializesCustomMapMadeWithSoftValues() throws Exception {
        ConcurrentMap<String, String> map = new MapMaker()
                .initialCapacity(4)
                .concurrencyLevel(2)
                .softValues()
                .makeMap();
        map.put("project", "sisu");
        map.put("component", "guice");

        byte[] serializedMap = serialize(map);
        ConcurrentMap<String, String> deserializedMap = deserialize(serializedMap);

        assertThat(deserializedMap).containsEntry("project", "sisu");
        assertThat(deserializedMap).containsEntry("component", "guice");
        assertThat(deserializedMap.putIfAbsent("runtime", "native-image")).isNull();
        assertThat(deserializedMap).containsEntry("runtime", "native-image");
    }

    private static byte[] serialize(ConcurrentMap<String, String> map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentMap<String, String> deserialize(byte[] serializedMap) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedMap))) {
            return (ConcurrentMap<String, String>) input.readObject();
        }
    }
}
