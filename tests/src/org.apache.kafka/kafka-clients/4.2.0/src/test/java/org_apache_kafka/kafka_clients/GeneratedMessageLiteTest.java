/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

public class GeneratedMessageLiteTest {
    private static final MethodHandle GET_DEFAULT_INSTANCE_HANDLE = findStaticMethod(
        "getDefaultInstance",
        MethodType.methodType(GeneratedMessageLite.class, Class.class)
    );
    private static final MethodHandle GET_METHOD_OR_DIE_HANDLE = findStaticMethod(
        "getMethodOrDie",
        MethodType.methodType(Method.class, Class.class, String.class, Class[].class)
    ).asFixedArity();
    private static final MethodHandle INVOKE_OR_DIE_HANDLE = findStaticMethod(
        "invokeOrDie",
        MethodType.methodType(Object.class, Method.class, Object.class, Object[].class)
    ).asFixedArity();
    private static final VarHandle DEFAULT_INSTANCE_MAP_HANDLE = findDefaultInstanceMapHandle();

    @Test
    void toStringUsesGeneratedAccessorMethods() {
        ReflectiveLiteMessage message = ReflectiveLiteMessage.of("kafka-clients");

        assertThat(message.toString()).contains("label: \"kafka-clients\"");
    }

    @Test
    void helperMethodsResolveAndInvokePublicAccessor() throws Throwable {
        ReflectiveLiteMessage message = ReflectiveLiteMessage.of("generated-message-lite");

        Method getLabel = (Method) GET_METHOD_OR_DIE_HANDLE.invokeWithArguments(
            ReflectiveLiteMessage.class,
            "getLabel",
            new Class<?>[0]
        );
        Object resolvedLabel = INVOKE_OR_DIE_HANDLE.invokeWithArguments(getLabel, message, new Object[0]);

        assertThat(getLabel.getName()).isEqualTo("getLabel");
        assertThat(resolvedLabel).isEqualTo("generated-message-lite");
    }

    @Test
    void getDefaultInstanceReinitializesMissingRegistrationFromClassName() throws Throwable {
        Map<Class<?>, GeneratedMessageLite<?, ?>> defaultInstanceMap = defaultInstanceMap();
        GeneratedMessageLite<?, ?> removed = defaultInstanceMap.remove(ReflectiveLiteMessage.class);

        assertThat(removed).isSameAs(ReflectiveLiteMessage.getDefaultInstance());

        try {
            Object resolvedDefaultInstance = GET_DEFAULT_INSTANCE_HANDLE.invokeWithArguments(ReflectiveLiteMessage.class);

            assertThat(resolvedDefaultInstance).isSameAs(ReflectiveLiteMessage.getDefaultInstance());
            assertThat(defaultInstanceMap.get(ReflectiveLiteMessage.class))
                .isSameAs(ReflectiveLiteMessage.getDefaultInstance());
        } finally {
            defaultInstanceMap.put(ReflectiveLiteMessage.class, ReflectiveLiteMessage.getDefaultInstance());
        }
    }

    private static MethodHandle findStaticMethod(String name, MethodType methodType) {
        try {
            return generatedMessageLiteLookup().findStatic(GeneratedMessageLite.class, name, methodType);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static VarHandle findDefaultInstanceMapHandle() {
        try {
            return generatedMessageLiteLookup().findStaticVarHandle(
                GeneratedMessageLite.class,
                "defaultInstanceMap",
                Map.class
            );
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static MethodHandles.Lookup generatedMessageLiteLookup() throws IllegalAccessException {
        return MethodHandles.privateLookupIn(GeneratedMessageLite.class, MethodHandles.lookup());
    }

    @SuppressWarnings("unchecked")
    private static Map<Class<?>, GeneratedMessageLite<?, ?>> defaultInstanceMap() {
        return (Map<Class<?>, GeneratedMessageLite<?, ?>>) DEFAULT_INSTANCE_MAP_HANDLE.get();
    }

    public static final class ReflectiveLiteMessage
        extends GeneratedMessageLite<ReflectiveLiteMessage, ReflectiveLiteMessage.Builder> {
        private static final ReflectiveLiteMessage DEFAULT_INSTANCE;
        private static volatile Parser<ReflectiveLiteMessage> parser;

        private String label_ = "";

        static {
            ReflectiveLiteMessage defaultInstance = new ReflectiveLiteMessage();
            DEFAULT_INSTANCE = defaultInstance;
            registerDefaultInstance(ReflectiveLiteMessage.class, defaultInstance);
        }

        private static ReflectiveLiteMessage of(String label) {
            ReflectiveLiteMessage message = new ReflectiveLiteMessage();
            message.setLabel(label);
            return message;
        }

        private static ReflectiveLiteMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public String getLabel() {
            return this.label_;
        }

        public boolean hasLabel() {
            return !this.label_.isEmpty();
        }

        void setLabel(String label) {
            this.label_ = label;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new ReflectiveLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<ReflectiveLiteMessage> resolvedParser = parser;
                    if (resolvedParser == null) {
                        synchronized (ReflectiveLiteMessage.class) {
                            resolvedParser = parser;
                            if (resolvedParser == null) {
                                resolvedParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                                parser = resolvedParser;
                            }
                        }
                    }
                    return resolvedParser;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        private static final class Builder extends GeneratedMessageLite.Builder<ReflectiveLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
