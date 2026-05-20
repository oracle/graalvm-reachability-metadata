/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.serialization.ListDeserializer;
import org.apache.kafka.common.serialization.ListSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ListDeserializerTest {

    @Test
    void deserializesIntoListWithSizeConstructor() {
        List<String> values = List.of("alpha", "beta");
        byte[] serialized = new ListSerializer<>(new StringSerializer()).serialize("topic", values);

        List<String> deserialized = new ListDeserializer<>(ArrayList.class, new StringDeserializer())
                .deserialize("topic", serialized);

        assertThat(deserialized)
                .isInstanceOf(ArrayList.class)
                .containsExactlyElementsOf(values);
    }

    @Test
    void deserializesIntoListWithDefaultConstructor() {
        List<String> values = List.of("gamma", "delta");
        byte[] serialized = new ListSerializer<>(new StringSerializer()).serialize("topic", values);

        List<String> deserialized = new ListDeserializer<>(DefaultConstructorStringList.class, new StringDeserializer())
                .deserialize("topic", serialized);

        assertThat(deserialized)
                .isInstanceOf(DefaultConstructorStringList.class)
                .containsExactlyElementsOf(values);
    }

    public static final class DefaultConstructorStringList extends ArrayList<String> {
    }
}
