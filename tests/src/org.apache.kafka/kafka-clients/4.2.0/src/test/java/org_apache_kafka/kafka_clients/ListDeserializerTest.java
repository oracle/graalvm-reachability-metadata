/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.kafka.common.serialization.ListDeserializer;
import org.apache.kafka.common.serialization.ListSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

public class ListDeserializerTest {
    @Test
    void deserializesIntoListWithCapacityConstructor() {
        List<String> original = Arrays.asList("alpha", null, "omega");
        byte[] serialized = serialize(original);
        ListDeserializer<String> deserializer = new ListDeserializer<>(arrayListClass(), new StringDeserializer());

        List<String> deserialized = deserializer.deserialize("list-deserializer-topic", serialized);

        assertInstanceOf(ArrayList.class, deserialized);
        assertEquals(original, deserialized);
    }

    @Test
    void deserializesIntoListWithDefaultConstructor() {
        List<String> original = Arrays.asList("first", null, "last");
        byte[] serialized = serialize(original);
        ListDeserializer<String> deserializer = new ListDeserializer<>(linkedListClass(), new StringDeserializer());

        List<String> deserialized = deserializer.deserialize("list-deserializer-topic", serialized);

        assertInstanceOf(LinkedList.class, deserialized);
        assertEquals(original, deserialized);
    }

    private static byte[] serialize(List<String> values) {
        ListSerializer<String> serializer = new ListSerializer<>(new StringSerializer());

        return serializer.serialize("list-deserializer-topic", values);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<ArrayList<String>> arrayListClass() {
        return (Class) ArrayList.class;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<LinkedList<String>> linkedListClass() {
        return (Class) LinkedList.class;
    }
}
