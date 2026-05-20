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

import java.io.StreamCorruptedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SerializerTest {

    @Test
    void roundTripsSerializableKafkaObject() throws Exception {
        TopicPartition partition = new TopicPartition("events", 2);

        Object deserialized = Serializer.deserialize(Serializer.serialize(partition));

        assertThat(deserialized).isEqualTo(partition);
    }

    @Test
    void opensClasspathResourceBeforeDeserializingIt() {
        assertThatThrownBy(() -> Serializer.deserialize("invalid-serialized-object.bin"))
                .isInstanceOf(StreamCorruptedException.class);
    }
}
