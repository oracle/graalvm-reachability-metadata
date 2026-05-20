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

public class SerializerTest {

    @Test
    void serializesAndDeserializesObjectPayload() throws Exception {
        byte[] bytes = Serializer.serialize("kafka");

        Object value = Serializer.deserialize(bytes);

        assertThat(value).isEqualTo("kafka");
    }

    @Test
    void deserializesObjectPayloadFromClasspathResource() throws Exception {
        Object value = Serializer.deserialize("serialized-string.bin");

        assertThat(value).isEqualTo("kafka");
    }
}
