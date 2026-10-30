/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_kafka.spring_kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.support.KafkaUtils;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SerializationUtilsAnonymous1Test {
    @Test
    void extractsTrustedKeyFailureAndRetainsApplicationHeaders() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 1, 12L, null, "body");
        record.headers().add("trace", new byte[]{6, 7});
        byte[] key = {1, 4, 9};
        SerializationUtils.deserializationException(record.headers(), key, new IllegalStateException("invalid key"), true);

        DeserializationException failure = SerializationUtils.getExceptionFromHeader(record,
                KafkaUtils.KEY_DESERIALIZER_EXCEPTION_HEADER, new LogAccessor(SerializationUtilsAnonymous1Test.class));

        assertNotNull(failure);
        assertTrue(failure.isKey());
        assertArrayEquals(key, failure.getData());
        assertEquals("invalid key", failure.getCause().getMessage());
        assertArrayEquals(new byte[]{6, 7}, failure.getHeaders().lastHeader("trace").value());
        assertNull(failure.getHeaders().lastHeader(KafkaUtils.KEY_DESERIALIZER_EXCEPTION_HEADER));
        assertNotNull(record.headers().lastHeader(KafkaUtils.KEY_DESERIALIZER_EXCEPTION_HEADER));
    }
}
