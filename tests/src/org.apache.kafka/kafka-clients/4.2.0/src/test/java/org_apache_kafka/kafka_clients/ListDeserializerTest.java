/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.common.serialization.ListDeserializer;
import org.apache.kafka.common.serialization.ListSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

public class ListDeserializerTest {

    @Test
    void deserializeUsesThePublicIntConstructorWhenTheListTypeProvidesOne() {
        List<String> input = List.of("alpha", "beta", "gamma");
        ListSerializer<String> serializer = new ListSerializer<>(new StringSerializer());
        ListDeserializer<String> deserializer = new ListDeserializer<>(CapacityTrackingStringList.class, new StringDeserializer());

        List<String> deserialized = deserializer.deserialize("topic", serializer.serialize("topic", input));

        assertThat(deserialized)
            .isInstanceOf(CapacityTrackingStringList.class)
            .containsExactlyElementsOf(input);
        assertThat(((CapacityTrackingStringList) deserialized).requestedCapacity()).isEqualTo(input.size());
    }

    @Test
    void deserializeFallsBackToThePublicNoArgConstructorWhenNoIntConstructorExists() {
        List<String> input = List.of("one", "two");
        ListSerializer<String> serializer = new ListSerializer<>(new StringSerializer());
        ListDeserializer<String> deserializer = new ListDeserializer<>(DefaultConstructorStringList.class, new StringDeserializer());

        List<String> deserialized = deserializer.deserialize("topic", serializer.serialize("topic", input));

        assertThat(deserialized)
            .isInstanceOf(DefaultConstructorStringList.class)
            .containsExactlyElementsOf(input);
        assertThat(((DefaultConstructorStringList) deserialized).constructedWithNoArgs()).isTrue();
    }

    public static final class CapacityTrackingStringList extends ArrayList<String> {
        private final int requestedCapacity;

        public CapacityTrackingStringList(int requestedCapacity) {
            super(requestedCapacity);
            this.requestedCapacity = requestedCapacity;
        }

        public int requestedCapacity() {
            return requestedCapacity;
        }
    }

    public static final class DefaultConstructorStringList extends ArrayList<String> {
        private final boolean constructedWithNoArgs;

        public DefaultConstructorStringList() {
            this.constructedWithNoArgs = true;
        }

        public boolean constructedWithNoArgs() {
            return constructedWithNoArgs;
        }
    }
}
