/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_kafka.spring_kafka;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.support.KafkaUtils;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.FailedDeserializationInfo;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ErrorHandlingDeserializerTest {
    @Test
    void configuresDelegateRecoveryFunctionAndValidatorByClassName() {
        try (ErrorHandlingDeserializer<String> deserializer = new ErrorHandlingDeserializer<>()) {
            deserializer.configure(Map.of(
                    ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, StringDeserializer.class.getName(),
                    ErrorHandlingDeserializer.VALUE_FUNCTION, RejectedOrderRecovery.class.getName(),
                    ErrorHandlingDeserializer.VALIDATOR_CLASS, OrderValidator.class.getName()), false);
            Headers headers = new RecordHeaders();
            byte[] rejected = "rejected".getBytes(StandardCharsets.UTF_8);

            assertEquals("orders:rejected", deserializer.deserialize("orders", headers, rejected));
            DeserializationException failure = SerializationUtils.byteArrayToDeserializationException(
                    new LogAccessor(ErrorHandlingDeserializerTest.class),
                    headers.lastHeader(KafkaUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER));
            assertNotNull(failure);
            assertArrayEquals(rejected, failure.getData());
            assertEquals(IllegalStateException.class, failure.getCause().getClass());

            assertEquals("accepted", deserializer.deserialize("orders", headers, "accepted".getBytes(StandardCharsets.UTF_8)));
            assertNull(headers.lastHeader(KafkaUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER));
        }
    }

    @Test
    void configuresKeyDelegateRecoveryFunctionAndValidatorWithClassObjects() {
        try (ErrorHandlingDeserializer<String> deserializer = new ErrorHandlingDeserializer<>()) {
            deserializer.configure(Map.of(
                    ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class,
                    ErrorHandlingDeserializer.KEY_FUNCTION, RejectedOrderRecovery.class,
                    ErrorHandlingDeserializer.VALIDATOR_CLASS, OrderValidator.class), true);

            assertEquals("keys:rejected", deserializer.deserialize("keys", "rejected".getBytes(StandardCharsets.UTF_8)));
            assertEquals("accepted", deserializer.deserialize("keys", "accepted".getBytes(StandardCharsets.UTF_8)));
        }
    }

    public static class RejectedOrderRecovery implements Function<FailedDeserializationInfo, String> {
        @Override
        public String apply(FailedDeserializationInfo failure) {
            return failure.getTopic() + ":" + new String(failure.getData(), StandardCharsets.UTF_8);
        }
    }

    public static class OrderValidator implements Validator {
        @Override
        public boolean supports(Class<?> type) {
            return String.class.equals(type);
        }

        @Override
        public void validate(Object target, Errors errors) {
            if ("rejected".equals(target)) {
                errors.reject("order.rejected", "Order is not accepted");
            }
        }
    }
}
