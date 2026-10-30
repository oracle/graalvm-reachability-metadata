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
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.DelegatingSerializer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class DelegatingSerializerTest {
    @Test
    void createsValueSerializersFromSelectorConfiguration() {
        try (DelegatingSerializer serializer = new DelegatingSerializer()) {
            serializer.configure(Map.of(DelegatingSerializer.VALUE_SERIALIZATION_SELECTOR_CONFIG,
                    "text:" + StringSerializer.class.getName() + ",number:" + IntegerSerializer.class.getName()), false);
            Headers textHeaders = new RecordHeaders().add(DelegatingSerializer.VALUE_SERIALIZATION_SELECTOR,
                    "text".getBytes(StandardCharsets.UTF_8));
            Headers numberHeaders = new RecordHeaders().add(DelegatingSerializer.VALUE_SERIALIZATION_SELECTOR,
                    "number".getBytes(StandardCharsets.UTF_8));

            assertArrayEquals("order".getBytes(StandardCharsets.UTF_8), serializer.serialize("orders", textHeaders, "order"));
            assertArrayEquals(new byte[]{0, 0, 0, 42}, serializer.serialize("orders", numberHeaders, 42));
        }
    }

    @Test
    void createsKeySerializerFromClassMappingAndAppliesEncoding() {
        try (DelegatingSerializer serializer = new DelegatingSerializer()) {
            serializer.configure(Map.of(DelegatingSerializer.KEY_SERIALIZATION_SELECTOR_CONFIG,
                    Map.of("text", StringSerializer.class), "key.serializer.encoding", "UTF-16BE"), true);
            Headers headers = new RecordHeaders().add(DelegatingSerializer.KEY_SERIALIZATION_SELECTOR,
                    "text".getBytes(StandardCharsets.UTF_8));

            assertArrayEquals(new byte[]{0, 65, 0, 66}, serializer.serialize("orders", headers, "AB"));
        }
    }
}
