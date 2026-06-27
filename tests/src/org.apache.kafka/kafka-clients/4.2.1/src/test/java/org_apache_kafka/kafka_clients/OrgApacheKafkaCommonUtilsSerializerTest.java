/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.utils.Serializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonUtilsSerializerTest {

    @Test
    void serializesAndDeserializesObjectBytes() throws Exception {
        byte[] serializedValue = Serializer.serialize("kafka-clients");
        Object deserializedValue = Serializer.deserialize(serializedValue);

        assertThat(deserializedValue).isEqualTo("kafka-clients");
    }

    @Test
    void deserializesObjectFromClasspathResource() throws Exception {
        Object deserializedValue = Serializer.deserialize(
                "org_apache_kafka/kafka_clients/serialized-kafka-string.bin");

        assertThat(deserializedValue).isEqualTo("kafka");
    }
}
