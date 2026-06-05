/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.utils.Serializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonUtilsSerializerTest {

    private static final String SERIALIZED_STRING_RESOURCE =
            "org_apache_kafka/kafka_clients/serializer-string.bin";

    @Test
    void serializesAndDeserializesObjectBytes() throws Exception {
        List<String> values = new ArrayList<>(List.of("alpha", "beta", "gamma"));

        byte[] serialized = Serializer.serialize(values);
        Object deserialized = Serializer.deserialize(serialized);

        assertThat(deserialized).isEqualTo(values);
    }

    @Test
    void deserializesObjectFromClasspathResource() throws Exception {
        Object deserialized = Serializer.deserialize(SERIALIZED_STRING_RESOURCE);

        assertThat(deserialized).isEqualTo("resource-value");
    }
}
