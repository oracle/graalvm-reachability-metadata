/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.serialization.ListDeserializer;
import org.apache.kafka.common.serialization.ListSerializer;
import org.apache.kafka.common.serialization.Serdes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonSerializationListDeserializerTest {

    @Test
    void deserializesArrayListUsingCapacityConstructor() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(CommonClientConfigs.DEFAULT_LIST_VALUE_SERDE_TYPE_CLASS, ArrayList.class);
        configs.put(CommonClientConfigs.DEFAULT_LIST_VALUE_SERDE_INNER_CLASS, Serdes.IntegerSerde.class);

        try (ListSerializer<Integer> serializer = new ListSerializer<>();
             ListDeserializer<Integer> deserializer = new ListDeserializer<>()) {
            serializer.configure(configs, false);
            deserializer.configure(configs, false);

            List<Integer> values = Arrays.asList(1, null, 3);
            byte[] serialized = serializer.serialize("list-topic", values);
            List<Integer> deserialized = deserializer.deserialize("list-topic", serialized);

            assertThat(deserialized)
                    .isInstanceOf(ArrayList.class)
                    .containsExactlyElementsOf(values);
        }
    }

    @Test
    void deserializesLinkedListUsingDefaultConstructor() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(CommonClientConfigs.DEFAULT_LIST_VALUE_SERDE_TYPE_CLASS, LinkedList.class);
        configs.put(CommonClientConfigs.DEFAULT_LIST_VALUE_SERDE_INNER_CLASS, Serdes.StringSerde.class);

        try (ListSerializer<String> serializer = new ListSerializer<>();
             ListDeserializer<String> deserializer = new ListDeserializer<>()) {
            serializer.configure(configs, false);
            deserializer.configure(configs, false);

            List<String> values = Arrays.asList("alpha", null, "omega");
            byte[] serialized = serializer.serialize("list-topic", values);
            List<String> deserialized = deserializer.deserialize("list-topic", serialized);

            assertThat(deserialized)
                    .isInstanceOf(LinkedList.class)
                    .containsExactlyElementsOf(values);
        }
    }
}
