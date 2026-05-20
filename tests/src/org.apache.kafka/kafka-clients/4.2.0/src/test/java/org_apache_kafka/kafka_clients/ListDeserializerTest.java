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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ListDeserializerTest {

    @Test
    void deserializesIntoListWithSizeConstructor() {
        List<String> values = Arrays.asList("one", "two");
        byte[] bytes = serialize(values);
        ListDeserializer<String> deserializer = configuredDeserializer(ArrayList.class);

        List<String> result = deserializer.deserialize("lists", bytes);

        assertThat(result)
                .isInstanceOf(ArrayList.class)
                .containsExactlyElementsOf(values);
    }

    @Test
    void deserializesIntoListWithDefaultConstructor() {
        List<String> values = Arrays.asList("red", "blue");
        byte[] bytes = serialize(values);
        ListDeserializer<String> deserializer = configuredDeserializer(LinkedList.class);

        List<String> result = deserializer.deserialize("lists", bytes);

        assertThat(result)
                .isInstanceOf(LinkedList.class)
                .containsExactlyElementsOf(values);
    }

    private static byte[] serialize(List<String> values) {
        ListSerializer<String> serializer = new ListSerializer<>();
        serializer.configure(Map.of(CommonClientConfigs.DEFAULT_LIST_VALUE_SERDE_INNER_CLASS, Serdes.StringSerde.class), false);
        return serializer.serialize("lists", values);
    }

    private static ListDeserializer<String> configuredDeserializer(Class<?> listClass) {
        ListDeserializer<String> deserializer = new ListDeserializer<>();
        deserializer.configure(Map.of(
                CommonClientConfigs.DEFAULT_LIST_VALUE_SERDE_TYPE_CLASS, listClass,
                CommonClientConfigs.DEFAULT_LIST_VALUE_SERDE_INNER_CLASS, Serdes.StringSerde.class), false);
        return deserializer;
    }
}
