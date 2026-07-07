/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_confluent.kafka_schema_registry_client;

import static org.assertj.core.api.Assertions.assertThat;

import io.confluent.kafka.schemaregistry.utils.BoundedConcurrentHashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class BoundedConcurrentHashMapTest {

    @Test
    void serializesPopulatedMapEntries() throws Exception {
        BoundedConcurrentHashMap<String, String> map = new BoundedConcurrentHashMap<>(8, 1);
        map.put("subject-a", "schema-a");
        map.put("subject-b", "schema-b");

        byte[] populatedBytes = serialize(map);
        byte[] emptyBytes = serialize(new BoundedConcurrentHashMap<String, String>(8, 1));

        assertThat(populatedBytes.length).isGreaterThan(emptyBytes.length);
    }

    @Test
    void deserializesEmptyMap() throws Exception {
        byte[] serialized = serialize(new BoundedConcurrentHashMap<String, String>(8, 1));

        BoundedConcurrentHashMap<?, ?> deserialized = deserialize(serialized);

        assertThat(deserialized).isEmpty();
    }

    private static byte[] serialize(BoundedConcurrentHashMap<String, String> map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static BoundedConcurrentHashMap<?, ?> deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (BoundedConcurrentHashMap<?, ?>) input.readObject();
        }
    }
}
