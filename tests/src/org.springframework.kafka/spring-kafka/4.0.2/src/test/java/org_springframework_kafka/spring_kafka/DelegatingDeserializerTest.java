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
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.DelegatingDeserializer;
import org.springframework.kafka.support.serializer.DelegatingSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DelegatingDeserializerTest {
    @Test
    void createsValueDeserializersFromSelectorConfiguration() {
        try (DelegatingDeserializer deserializer = new DelegatingDeserializer()) {
            deserializer.configure(Map.of(DelegatingSerializer.VALUE_SERIALIZATION_SELECTOR_CONFIG,
                    "text:" + StringDeserializer.class.getName() + ",number:" + IntegerDeserializer.class.getName()), false);
            Headers textHeaders = new RecordHeaders().add(DelegatingSerializer.VALUE_SERIALIZATION_SELECTOR,
                    "text".getBytes(StandardCharsets.UTF_8));
            Headers numberHeaders = new RecordHeaders().add(DelegatingSerializer.VALUE_SERIALIZATION_SELECTOR,
                    "number".getBytes(StandardCharsets.UTF_8));

            assertEquals("order", deserializer.deserialize("orders", textHeaders, "order".getBytes(StandardCharsets.UTF_8)));
            assertEquals(42, deserializer.deserialize("orders", numberHeaders, new byte[]{0, 0, 0, 42}));
        }
    }

    @Test
    void createsKeyDeserializerFromClassMappingAndAppliesEncoding() {
        try (DelegatingDeserializer deserializer = new DelegatingDeserializer()) {
            deserializer.configure(Map.of(DelegatingSerializer.KEY_SERIALIZATION_SELECTOR_CONFIG,
                    Map.of("text", StringDeserializer.class), "key.deserializer.encoding", "UTF-16BE"), true);
            Headers headers = new RecordHeaders().add(DelegatingSerializer.KEY_SERIALIZATION_SELECTOR,
                    "text".getBytes(StandardCharsets.UTF_8));

            assertEquals("AB", deserializer.deserialize("orders", headers, new byte[]{0, 65, 0, 66}));
        }
    }
}
