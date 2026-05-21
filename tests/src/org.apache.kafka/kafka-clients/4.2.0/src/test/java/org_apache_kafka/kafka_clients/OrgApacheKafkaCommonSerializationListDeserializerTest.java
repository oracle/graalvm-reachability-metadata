/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonSerializationListDeserializerTest {

    @Test
    void deserializesIntoListTypeWithCapacityConstructor() {
        Serde<List<String>> serde = Serdes.ListSerde(ArrayList.class, Serdes.String());
        List<String> values = Arrays.asList("alpha", "beta", "gamma");

        byte[] serialized = serde.serializer().serialize("topic", values);
        List<String> deserialized = serde.deserializer().deserialize("topic", serialized);

        assertThat(deserialized)
                .isInstanceOf(ArrayList.class)
                .containsExactlyElementsOf(values);
    }

    @Test
    void deserializesIntoListTypeWithDefaultConstructor() {
        Serde<List<String>> serde = Serdes.ListSerde(LinkedList.class, Serdes.String());
        List<String> values = Arrays.asList("one", null, "three");

        byte[] serialized = serde.serializer().serialize("topic", values);
        List<String> deserialized = serde.deserializer().deserialize("topic", serialized);

        assertThat(deserialized)
                .isInstanceOf(LinkedList.class)
                .containsExactlyElementsOf(values);
    }
}
