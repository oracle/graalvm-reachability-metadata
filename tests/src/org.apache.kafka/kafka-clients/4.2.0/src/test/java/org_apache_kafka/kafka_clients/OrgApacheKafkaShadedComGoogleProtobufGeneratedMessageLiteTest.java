/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageLiteTest {

    private static final MethodHandle GET_METHOD_OR_DIE = findGeneratedMessageLiteHelper(
            "getMethodOrDie",
            MethodType.methodType(Method.class, Class.class, String.class, Class[].class));
    private static final MethodHandle INVOKE_OR_DIE = findGeneratedMessageLiteHelper(
            "invokeOrDie",
            MethodType.methodType(Object.class, Method.class, Object.class, Object[].class));

    @Test
    void generatedRuntimeHelpersResolveAndInvokeGeneratedAccessors() throws Throwable {
        GeneratedAccessors accessors = new GeneratedAccessors("native-image");

        Method instanceAccessor = getMethodOrDie(GeneratedAccessors.class, "describe", String.class);
        Method staticAccessor = getMethodOrDie(GeneratedAccessors.class, "defaultDescription");

        assertThat(invokeOrDie(instanceAccessor, accessors, "protobuf"))
                .isEqualTo("native-image:protobuf");
        assertThat(invokeOrDie(staticAccessor, null))
                .isEqualTo("generated-message-lite");
    }

    private static MethodHandle findGeneratedMessageLiteHelper(String name, MethodType methodType) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    GeneratedMessageLite.class,
                    MethodHandles.lookup());
            return lookup.findStatic(GeneratedMessageLite.class, name, methodType);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Method getMethodOrDie(Class<?> type, String name, Class<?>... parameterTypes) throws Throwable {
        return (Method) GET_METHOD_OR_DIE.invoke(type, name, parameterTypes);
    }

    private static Object invokeOrDie(Method method, Object target, Object... arguments) throws Throwable {
        return INVOKE_OR_DIE.invoke(method, target, arguments);
    }

    public static final class GeneratedAccessors {
        private final String prefix;

        public GeneratedAccessors(String prefix) {
            this.prefix = prefix;
        }

        public String describe(String suffix) {
            return prefix + ":" + suffix;
        }

        public static String defaultDescription() {
            return "generated-message-lite";
        }
    }
}
