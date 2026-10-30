/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_kafka.spring_kafka;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.DelegatingByTopicDeserializer;
import org.springframework.kafka.support.serializer.DelegatingByTopicSerialization;
import org.springframework.kafka.support.serializer.DelegatingByTopicSerializer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DelegatingByTopicSerializationTest {
    @Test
    void createsTopicSerializerAndDefaultFromClassNames() {
        try (DelegatingByTopicSerializer serializer = new DelegatingByTopicSerializer()) {
            serializer.configure(Map.of(
                    DelegatingByTopicSerialization.VALUE_SERIALIZATION_TOPIC_CONFIG,
                    "orders-.*:" + StringSerializer.class.getName(),
                    DelegatingByTopicSerialization.VALUE_SERIALIZATION_TOPIC_DEFAULT, IntegerSerializer.class.getName()), false);
            Headers headers = new RecordHeaders();

            assertArrayEquals("ready".getBytes(StandardCharsets.UTF_8), serializer.serialize("orders-created", headers, "ready"));
            assertArrayEquals(new byte[]{0, 0, 0, 9}, serializer.serialize("counts", headers, 9));
        }
    }

    @Test
    void createsTopicDeserializerAndDefaultFromClassMapping() {
        try (DelegatingByTopicDeserializer deserializer = new DelegatingByTopicDeserializer()) {
            deserializer.configure(Map.of(
                    DelegatingByTopicSerialization.KEY_SERIALIZATION_TOPIC_CONFIG,
                    Map.of("orders-.*", StringDeserializer.class),
                    DelegatingByTopicSerialization.KEY_SERIALIZATION_TOPIC_DEFAULT, IntegerDeserializer.class), true);
            Headers headers = new RecordHeaders();

            assertEquals("ready", deserializer.deserialize("orders-created", headers, "ready".getBytes(StandardCharsets.UTF_8)));
            assertEquals(9, deserializer.deserialize("counts", headers, new byte[]{0, 0, 0, 9}));
        }
    }
}
