/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageLiteInnerSerializedFormTest {

    @Test
    void restoresGeneratedLiteMessageThroughDefaultInstanceField() throws Exception {
        StandardLiteMessage message = StandardLiteMessage.getDefaultInstance();

        Object restored = roundTrip(message.serializedForm());

        assertThat(restored).isInstanceOf(StandardLiteMessage.class);
        assertThat(((StandardLiteMessage) restored).isInitialized()).isTrue();
    }

    @Test
    void restoresLegacyLiteMessageThroughClassNameAndFallbackDefaultInstanceField() throws Exception {
        LegacyLiteMessage message = LegacyLiteMessage.getDefaultInstance();

        Object restored = roundTripWithMessageClassErased(message.serializedForm(), LegacyLiteMessage.class);

        assertThat(restored).isInstanceOf(LegacyLiteMessage.class);
        assertThat(((LegacyLiteMessage) restored).isInitialized()).isTrue();
    }

    private static Object roundTrip(Object value) throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
            serialized = bytes.toByteArray();
        }
        return readObject(serialized);
    }

    private static Object roundTripWithMessageClassErased(Object value, Class<?> erasedClass)
            throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new MessageClassErasingObjectOutputStream(bytes, erasedClass)) {
            output.writeObject(value);
            serialized = bytes.toByteArray();
        }
        return readObject(serialized);
    }

    private static Object readObject(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return input.readObject();
        }
    }

    private static final class MessageClassErasingObjectOutputStream extends ObjectOutputStream {
        private final Class<?> erasedClass;

        private MessageClassErasingObjectOutputStream(OutputStream output, Class<?> erasedClass) throws IOException {
            super(output);
            this.erasedClass = erasedClass;
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (object == erasedClass) {
                return null;
            }
            return object;
        }
    }

    public static final class StandardLiteMessage
            extends GeneratedMessageLite<StandardLiteMessage, StandardLiteMessage.Builder> {
        private static final StandardLiteMessage DEFAULT_INSTANCE = new StandardLiteMessage();
        private static volatile Parser<StandardLiteMessage> parser;

        static {
            DEFAULT_INSTANCE.makeImmutable();
        }

        private StandardLiteMessage() {
        }

        public static StandardLiteMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        Object serializedForm() {
            return SerializedForm.of(this);
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object argument0, Object argument1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new StandardLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<StandardLiteMessage> localParser = parser;
                    if (localParser == null) {
                        localParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                        parser = localParser;
                    }
                    return localParser;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        public static final class Builder extends GeneratedMessageLite.Builder<StandardLiteMessage, Builder> {

            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class LegacyLiteMessage
            extends GeneratedMessageLite<LegacyLiteMessage, LegacyLiteMessage.Builder> {
        private static final LegacyLiteMessage defaultInstance = new LegacyLiteMessage();
        private static volatile Parser<LegacyLiteMessage> parser;

        static {
            defaultInstance.makeImmutable();
        }

        private LegacyLiteMessage() {
        }

        public static LegacyLiteMessage getDefaultInstance() {
            return defaultInstance;
        }

        Object serializedForm() {
            return SerializedForm.of(this);
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object argument0, Object argument1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new LegacyLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(defaultInstance, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return defaultInstance;
                case GET_PARSER:
                    Parser<LegacyLiteMessage> localParser = parser;
                    if (localParser == null) {
                        localParser = new DefaultInstanceBasedParser<>(defaultInstance);
                        parser = localParser;
                    }
                    return localParser;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        public static final class Builder extends GeneratedMessageLite.Builder<LegacyLiteMessage, Builder> {

            private Builder() {
                super(defaultInstance);
            }
        }
    }
}
