/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.utils.Serializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OrgApacheKafkaCommonUtilsSerializerTest {

    @Test
    void serializesAndDeserializesObjects() throws Exception {
        List<String> payload = new ArrayList<>(Arrays.asList("alpha", "beta"));

        byte[] serialized = Serializer.serialize(payload);
        Object deserialized = Serializer.deserialize(serialized);

        assertThat(deserialized).isEqualTo(payload);
    }

    @Test
    void attemptsToDeserializeNamedClasspathResource() {
        assertThatThrownBy(() -> Serializer.deserialize("kafka/kafka-version.properties"))
                .isInstanceOfAny(IOException.class, NullPointerException.class);
    }
}
