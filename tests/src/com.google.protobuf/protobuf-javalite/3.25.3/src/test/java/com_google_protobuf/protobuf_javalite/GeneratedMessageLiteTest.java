/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static java.lang.invoke.MethodType.methodType;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class GeneratedMessageLiteTest {
    private static final String LABEL = "visible";
    private static final List<String> TAGS = List.of("red", "blue");
    private static final Map<String, Integer> COUNTS = orderedCounts();

    @Test
    void generatedMessageInfoLookupInitializesDefaultInstanceClass() throws Throwable {
        MethodHandle getDefaultInstance = privateLookup().findStatic(
                GeneratedMessageLite.class,
                "getDefaultInstance",
                methodType(GeneratedMessageLite.class, Class.class)
        );

        GeneratedMessageLite<?, ?> defaultInstance =
                (GeneratedMessageLite<?, ?>) getDefaultInstance.invoke(
                        LazyRegisteredLiteMessage.class
                );

        assertThat(defaultInstance).isInstanceOf(LazyRegisteredLiteMessage.class);
        assertThat(defaultInstance).isSameAs(LazyRegisteredLiteMessage.getDefaultInstance());
    }

    @Test
    void generatedMethodHelpersResolveAndInvokeGeneratedAccessors() throws Throwable {
        MethodHandle getMethodOrDie = privateLookup().findStatic(
                GeneratedMessageLite.class,
                "getMethodOrDie",
                methodType(Method.class, Class.class, String.class, Class[].class)
        ).asFixedArity();
        MethodHandle invokeOrDie = privateLookup().findStatic(
                GeneratedMessageLite.class,
                "invokeOrDie",
                methodType(Object.class, Method.class, Object.class, Object[].class)
        ).asFixedArity();

        Method accessor = (Method) getMethodOrDie.invokeWithArguments(
                PrintableLiteMessage.class,
                "getLabel",
                new Class<?>[0]
        );
        Object value = invokeOrDie.invokeWithArguments(
                accessor,
                new PrintableLiteMessage(),
                new Object[0]
        );

        assertThat(value).isEqualTo(LABEL);
    }

    @Test
    void toStringReflectivelyPrintsGeneratedLiteAccessors() {
        String printed = new PrintableLiteMessage().toString();

        assertThat(printed)
                .contains("label: \"visible\"")
                .contains("tags: \"red\"")
                .contains("tags: \"blue\"")
                .contains("key: \"errors\"")
                .contains("value: 7")
                .contains("key: \"warnings\"")
                .contains("value: 3");
    }

    private static MethodHandles.Lookup privateLookup() throws IllegalAccessException {
        return MethodHandles.privateLookupIn(GeneratedMessageLite.class, MethodHandles.lookup());
    }

    private static Map<String, Integer> orderedCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("errors", 7);
        counts.put("warnings", 3);
        return Collections.unmodifiableMap(counts);
    }

    public static final class PrintableLiteMessage
            extends GeneratedMessageLite<PrintableLiteMessage, PrintableLiteMessage.Builder> {
        private static final PrintableLiteMessage DEFAULT_INSTANCE = new PrintableLiteMessage();
        private static volatile Parser<PrintableLiteMessage> parser;

        public String getLabel() {
            return LABEL;
        }

        public boolean hasLabel() {
            return true;
        }

        @SuppressWarnings("unused")
        public void setLabel(String label) {
            throw new UnsupportedOperationException(
                    "Setter exists only to mirror generated message accessors"
            );
        }

        public List<String> getTagsList() {
            return TAGS;
        }

        public Map<String, Integer> getCountsMap() {
            return COUNTS;
        }

        @Override
        protected Object dynamicMethod(
                MethodToInvoke method,
                Object firstArgument,
                Object secondArgument
        ) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new PrintableLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<PrintableLiteMessage> localParser = parser;
                    if (localParser == null) {
                        synchronized (PrintableLiteMessage.class) {
                            localParser = parser;
                            if (localParser == null) {
                                localParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                                parser = localParser;
                            }
                        }
                    }
                    return localParser;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException(
                            "Dynamic operation is not needed for this coverage test"
                    );
            }
        }

        public static final class Builder
                extends GeneratedMessageLite.Builder<PrintableLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }

    public static final class LazyRegisteredLiteMessage extends GeneratedMessageLite<
            LazyRegisteredLiteMessage,
            LazyRegisteredLiteMessage.Builder> {
        private static final LazyRegisteredLiteMessage DEFAULT_INSTANCE;
        private static volatile Parser<LazyRegisteredLiteMessage> parser;

        static {
            LazyRegisteredLiteMessage instance = new LazyRegisteredLiteMessage();
            DEFAULT_INSTANCE = instance;
            registerDefaultInstance(LazyRegisteredLiteMessage.class, instance);
        }

        private LazyRegisteredLiteMessage() {
        }

        public static LazyRegisteredLiteMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected Object dynamicMethod(
                MethodToInvoke method,
                Object firstArgument,
                Object secondArgument
        ) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new LazyRegisteredLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<LazyRegisteredLiteMessage> localParser = parser;
                    if (localParser == null) {
                        synchronized (LazyRegisteredLiteMessage.class) {
                            localParser = parser;
                            if (localParser == null) {
                                localParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                                parser = localParser;
                            }
                        }
                    }
                    return localParser;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException(
                            "Dynamic operation is not needed for this coverage test"
                    );
            }
        }

        public static final class Builder
                extends GeneratedMessageLite.Builder<LazyRegisteredLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }

            @Override
            public Builder mergeFrom(
                    CodedInputStream input,
                    ExtensionRegistryLite extensionRegistry
            ) throws IOException {
                while (input.readTag() != 0) {
                    if (!input.skipField(input.getLastTag())) {
                        break;
                    }
                }
                return this;
            }

            @Override
            public Builder mergeFrom(
                    byte[] data,
                    int offset,
                    int length,
                    ExtensionRegistryLite extensionRegistry
            ) throws InvalidProtocolBufferException {
                if (length != 0) {
                    throw new InvalidProtocolBufferException("Expected an empty message");
                }
                return this;
            }
        }
    }
}
