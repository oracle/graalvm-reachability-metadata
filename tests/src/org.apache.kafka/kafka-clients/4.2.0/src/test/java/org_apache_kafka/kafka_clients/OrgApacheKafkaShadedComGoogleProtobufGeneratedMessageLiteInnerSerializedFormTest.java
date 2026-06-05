/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite.MethodToInvoke;
import org.apache.kafka.shaded.com.google.protobuf.MessageLite;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageLiteInnerSerializedFormTest {
    @Test
    void serializedFormRestoresMessageFromDefaultInstanceField() throws Exception {
        DefaultInstanceMessage message = DefaultInstanceMessage.newBuilder().buildPartial();

        Object restored = deserialize(serialize(serializedForm(message)));

        assertThat(restored).isEqualTo(message);
    }

    @Test
    void serializedFormRestoresMessageUsingLegacyClassNameField() throws Exception {
        DefaultInstanceMessage message = DefaultInstanceMessage.newBuilder().buildPartial();

        Object restored = deserialize(serializeWithNullMessageClass(
                serializedForm(message), DefaultInstanceMessage.class));

        assertThat(restored).isEqualTo(message);
    }

    @Test
    void serializedFormRestoresMessageFromLegacyDefaultInstanceField() throws Exception {
        LegacyDefaultInstanceMessage message = LegacyDefaultInstanceMessage
                .newBuilder()
                .buildPartial();

        Object restored = deserialize(serialize(serializedForm(message)));

        assertThat(restored).isEqualTo(message);
    }

    private static Object serializedForm(MessageLite message) {
        return DefaultInstanceMessage.serializedForm(message);
    }

    private static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static byte[] serializeWithNullMessageClass(Object object, Class<?> messageClass)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (NullingMessageClassObjectOutputStream output =
                new NullingMessageClassObjectOutputStream(bytes, messageClass)) {
            output.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }

    private static final class NullingMessageClassObjectOutputStream extends ObjectOutputStream {
        private final Class<?> messageClass;

        private NullingMessageClassObjectOutputStream(
                ByteArrayOutputStream output, Class<?> messageClass) throws IOException {
            super(output);
            this.messageClass = messageClass;
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (object == messageClass) {
                return null;
            }
            return super.replaceObject(object);
        }
    }

    @SuppressWarnings("serial")
    private static final class DefaultInstanceMessage extends GeneratedMessageLite<
            DefaultInstanceMessage, DefaultInstanceMessage.Builder> {
        private static final DefaultInstanceMessage DEFAULT_INSTANCE = new DefaultInstanceMessage();

        static {
            registerDefaultInstance(DefaultInstanceMessage.class, DEFAULT_INSTANCE);
        }

        private static volatile Parser<DefaultInstanceMessage> parser;

        private DefaultInstanceMessage() {
        }

        private static Builder newBuilder() {
            return DEFAULT_INSTANCE.createBuilder();
        }

        private static Object serializedForm(MessageLite message) {
            return SerializedForm.of(message);
        }

        @Override
        protected Object dynamicMethod(
                MethodToInvoke method, Object firstArgument, Object secondArgument) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new DefaultInstanceMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    return parser();
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException("Unsupported method: " + method);
            }
        }

        private static Parser<DefaultInstanceMessage> parser() {
            Parser<DefaultInstanceMessage> result = parser;
            if (result == null) {
                synchronized (DefaultInstanceMessage.class) {
                    result = parser;
                    if (result == null) {
                        result = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                        parser = result;
                    }
                }
            }
            return result;
        }

        private static final class Builder
                extends GeneratedMessageLite.Builder<DefaultInstanceMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }

    @SuppressWarnings("serial")
    private static final class LegacyDefaultInstanceMessage extends GeneratedMessageLite<
            LegacyDefaultInstanceMessage, LegacyDefaultInstanceMessage.Builder> {
        @SuppressWarnings("checkstyle:ConstantName")
        private static final LegacyDefaultInstanceMessage defaultInstance =
                new LegacyDefaultInstanceMessage();

        static {
            registerDefaultInstance(LegacyDefaultInstanceMessage.class, defaultInstance);
        }

        private static volatile Parser<LegacyDefaultInstanceMessage> parser;

        private LegacyDefaultInstanceMessage() {
        }

        private static Builder newBuilder() {
            return defaultInstance.createBuilder();
        }

        @Override
        protected Object dynamicMethod(
                MethodToInvoke method, Object firstArgument, Object secondArgument) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new LegacyDefaultInstanceMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(defaultInstance, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return defaultInstance;
                case GET_PARSER:
                    return parser();
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException("Unsupported method: " + method);
            }
        }

        private static Parser<LegacyDefaultInstanceMessage> parser() {
            Parser<LegacyDefaultInstanceMessage> result = parser;
            if (result == null) {
                synchronized (LegacyDefaultInstanceMessage.class) {
                    result = parser;
                    if (result == null) {
                        result = new DefaultInstanceBasedParser<>(defaultInstance);
                        parser = result;
                    }
                }
            }
            return result;
        }

        private static final class Builder
                extends GeneratedMessageLite.Builder<LegacyDefaultInstanceMessage, Builder> {
            private Builder() {
                super(defaultInstance);
            }
        }
    }
}
