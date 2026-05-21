/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.utils.Serializer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonUtilsSerializerTest {

    private static final String SERIALIZED_RESOURCE = "org_apache_kafka/kafka_clients/serialized-string.bin";

    @Test
    void serializesAndDeserializesObjectBytes() throws Exception {
        String value = "round-trip-value";

        byte[] serialized = Serializer.serialize(value);
        Object deserialized = Serializer.deserialize(new ByteArrayInputStream(serialized));

        assertThat(deserialized).isEqualTo(value);
    }

    @Test
    void deserializesObjectFromClasspathResource() throws Exception {
        Object deserialized = Serializer.deserialize(SERIALIZED_RESOURCE);

        assertThat(deserialized).isEqualTo("resource-value");
    }
}
