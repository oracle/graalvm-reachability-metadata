/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_kafka.spring_kafka;

import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.support.KafkaUtils;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SerializationUtilsTest {
    @Test
    void invokesConfiguredPayloadParser() {
        BiFunction<String, Headers, Integer> parser = SerializationUtils.propertyToMethodInvokingFunction(
                Integer.class.getName() + ".valueOf", String.class, getClass().getClassLoader());

        assertEquals(73, parser.apply("73", new RecordHeaders()));
    }

    @Test
    void invokesConfiguredParserWithHeaders() {
        Headers headers = new RecordHeaders().add("prefix", "order-".getBytes(StandardCharsets.UTF_8));
        BiFunction<String, Headers, String> parser = SerializationUtils.propertyToMethodInvokingFunction(
                SerializationUtilsTest.class.getName() + ".parseOrder", String.class, getClass().getClassLoader());

        assertEquals("order-42", parser.apply("42", headers));
    }

    @Test
    void preservesDeserializationFailureInValueHeader() {
        Headers headers = new RecordHeaders();
        byte[] payload = "invalid order".getBytes(StandardCharsets.UTF_8);
        SerializationUtils.deserializationException(headers, payload, new IllegalArgumentException("invalid payload"), false);

        DeserializationException failure = SerializationUtils.byteArrayToDeserializationException(
                new LogAccessor(SerializationUtilsTest.class), headers.lastHeader(KafkaUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER));

        assertNotNull(failure);
        assertFalse(failure.isKey());
        assertArrayEquals(payload, failure.getData());
        assertEquals(IllegalArgumentException.class, failure.getCause().getClass());
        assertEquals("invalid payload", failure.getCause().getMessage());
    }

    @Test
    void retainsFailureDetailsWhenOriginalExceptionCannotBeSerialized() {
        Headers headers = new RecordHeaders();
        byte[] payload = {5, 8};
        SerializationUtils.deserializationException(headers, payload, new PayloadException("unreadable key"), true);

        DeserializationException failure = SerializationUtils.byteArrayToDeserializationException(
                new LogAccessor(SerializationUtilsTest.class), headers.lastHeader(KafkaUtils.KEY_DESERIALIZER_EXCEPTION_HEADER));

        assertNotNull(failure);
        assertTrue(failure.isKey());
        assertArrayEquals(payload, failure.getData());
        assertEquals(RuntimeException.class, failure.getCause().getClass());
        assertTrue(failure.getCause().getMessage().contains(PayloadException.class.getName()));
        assertTrue(failure.getCause().getMessage().contains("unreadable key"));
    }

    public static String parseOrder(String payload, Headers headers) {
        return new String(headers.lastHeader("prefix").value(), StandardCharsets.UTF_8) + payload;
    }

    public static class PayloadException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final Object context = new Object();

        public PayloadException(String message) {
            super(message);
        }
    }
}
