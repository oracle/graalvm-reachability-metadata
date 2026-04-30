/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.junit.jupiter.api.Test;

public class GeneratedMessageLiteTest {
    private static final MethodHandle GET_METHOD_OR_DIE = generatedMessageLiteStaticHandle(
            "getMethodOrDie",
            MethodType.methodType(Method.class, Class.class, String.class, Class[].class));
    private static final MethodHandle INVOKE_OR_DIE = generatedMessageLiteStaticHandle(
            "invokeOrDie",
            MethodType.methodType(Object.class, Method.class, Object.class, Object[].class));

    @Test
    void invokesGeneratedAccessorThroughGeneratedMessageLiteHelpers() throws Throwable {
        Method probeMethod = (Method) GET_METHOD_OR_DIE.invoke(
                PublicAccessorProbe.class,
                "probeValue",
                new Class<?>[0]);
        Object value = INVOKE_OR_DIE.invoke(probeMethod, new PublicAccessorProbe("covered"), new Object[0]);

        assertEquals("covered", value);
    }

    private static MethodHandle generatedMessageLiteStaticHandle(String name, MethodType methodType) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    GeneratedMessageLite.class,
                    MethodHandles.lookup());
            return lookup.findStatic(GeneratedMessageLite.class, name, methodType);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Expected GeneratedMessageLite helper to be available", exception);
        }
    }

    public static final class PublicAccessorProbe {
        private final String value;

        private PublicAccessorProbe(String value) {
            this.value = value;
        }

        public String probeValue() {
            return value;
        }
    }
}
