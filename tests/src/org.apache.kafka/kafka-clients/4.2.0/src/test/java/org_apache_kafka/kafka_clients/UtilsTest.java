/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicCollection;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Utils;
import org.junit.jupiter.api.Test;

public class UtilsTest {
    @Test
    void instantiatesSerializerFromClassName() throws Exception {
        StringSerializer serializer = Utils.newInstance(StringSerializer.class.getName(), StringSerializer.class);

        assertInstanceOf(StringSerializer.class, serializer);
        assertArrayEquals("value".getBytes(StandardCharsets.UTF_8), serializer.serialize("utils-topic", "value"));
    }

    @Test
    void instantiatesProducerRecordWithPublicParameterizedConstructor() throws Exception {
        @SuppressWarnings("unchecked")
        ProducerRecord<String, String> record = Utils.newParameterizedInstance(
                ProducerRecord.class.getName(),
                String.class,
                "utils-topic",
                Object.class,
                "record-value");

        assertEquals("utils-topic", record.topic());
        assertEquals("record-value", record.value());
        assertNull(record.key());
        assertNull(record.partition());
    }

    @Test
    void reportsAbstractKafkaSubclasses() {
        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> Utils.ensureConcreteSubclass(TopicCollection.class, TopicCollection.class));

        assertTrue(exception.getMessage().contains("This class is abstract and cannot be created."));
    }
}
