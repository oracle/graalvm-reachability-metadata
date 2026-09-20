/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.utils.Serializer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonUtilsSerializerTest {

    @Test
    void serializesAndDeserializesKafkaObjectBytes() throws Exception {
        TopicPartition originalValue = new TopicPartition("orders", 3);

        byte[] serializedValue = Serializer.serialize(originalValue);

        assertThat(serializedValue).isNotEmpty();
        try (ByteArrayInputStream input = new ByteArrayInputStream(serializedValue)) {
            Object deserializedValue = Serializer.deserialize(input);
            assertThat(deserializedValue).isEqualTo(originalValue);
        }
    }

    @Test
    void deserializesCompatibleKafkaObjectFromClasspathResource() throws Exception {
        Object deserializedValue = Serializer.deserialize("serializedData/topicPartitionSerializedfile");

        assertThat(deserializedValue).isEqualTo(new TopicPartition("mytopic", 5));
    }
}
