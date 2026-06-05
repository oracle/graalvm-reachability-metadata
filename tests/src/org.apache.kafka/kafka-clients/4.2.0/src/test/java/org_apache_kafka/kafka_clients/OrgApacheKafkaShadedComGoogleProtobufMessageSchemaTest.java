/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite.MethodToInvoke;
import org.apache.kafka.shaded.com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

public class OrgApacheKafkaShadedComGoogleProtobufMessageSchemaTest {
    private static final String SINGLE_INT32_FIELD_MESSAGE_INFO = "\u0000\u0001\u0000\u0000"
            + "\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004";

    @Test
    void generatedLiteMessageSchemaSerializesAndParsesScalarFields()
            throws InvalidProtocolBufferException {
        SimpleLiteMessage message = SimpleLiteMessage.newBuilder()
                .setValue(150)
                .build();

        byte[] serialized = message.toByteArray();
        SimpleLiteMessage parsed = SimpleLiteMessage.parseFrom(serialized);

        assertThat(parsed.getValue()).isEqualTo(150);
        assertThat(serialized).isNotEmpty();
    }

    @Test
    void generatedLiteMessageSchemaRejectsInconsistentFieldMetadata() {
        assertThatThrownBy(() -> MissingFieldLiteMessage.of(7).toByteArray())
                .isInstanceOf(RuntimeException.class);
    }

    @SuppressWarnings({"checkstyle:MemberName", "serial"})
    public static final class SimpleLiteMessage extends GeneratedMessageLite<
            SimpleLiteMessage, SimpleLiteMessage.Builder> {
        private static final SimpleLiteMessage DEFAULT_INSTANCE = new SimpleLiteMessage();

        static {
            registerDefaultInstance(SimpleLiteMessage.class, DEFAULT_INSTANCE);
        }

        private static volatile Parser<SimpleLiteMessage> parser;
        private int value_;

        private SimpleLiteMessage() {
        }

        public static Builder newBuilder() {
            return DEFAULT_INSTANCE.createBuilder();
        }

        public static SimpleLiteMessage parseFrom(byte[] data)
                throws InvalidProtocolBufferException {
            return GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, data);
        }

        public int getValue() {
            return value_;
        }

        @Override
        protected Object dynamicMethod(
                MethodToInvoke method, Object firstArgument, Object secondArgument) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new SimpleLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(
                            DEFAULT_INSTANCE,
                            SINGLE_INT32_FIELD_MESSAGE_INFO,
                            new Object[] {"value_"});
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

        private static Parser<SimpleLiteMessage> parser() {
            Parser<SimpleLiteMessage> result = parser;
            if (result == null) {
                synchronized (SimpleLiteMessage.class) {
                    result = parser;
                    if (result == null) {
                        result = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                        parser = result;
                    }
                }
            }
            return result;
        }

        public static final class Builder
                extends GeneratedMessageLite.Builder<SimpleLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }

            public Builder setValue(int value) {
                copyOnWrite();
                instance.value_ = value;
                return this;
            }
        }
    }

    @SuppressWarnings({"checkstyle:MemberName", "serial"})
    public static final class MissingFieldLiteMessage extends GeneratedMessageLite<
            MissingFieldLiteMessage, MissingFieldLiteMessage.Builder> {
        private static final MissingFieldLiteMessage DEFAULT_INSTANCE =
                new MissingFieldLiteMessage();

        static {
            registerDefaultInstance(MissingFieldLiteMessage.class, DEFAULT_INSTANCE);
        }

        private static volatile Parser<MissingFieldLiteMessage> parser;
        private int value_;

        private MissingFieldLiteMessage() {
        }

        private static MissingFieldLiteMessage of(int value) {
            MissingFieldLiteMessage message = new MissingFieldLiteMessage();
            message.value_ = value;
            return message;
        }

        @Override
        protected Object dynamicMethod(
                MethodToInvoke method, Object firstArgument, Object secondArgument) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new MissingFieldLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(
                            DEFAULT_INSTANCE,
                            SINGLE_INT32_FIELD_MESSAGE_INFO,
                            new Object[] {"missing_"});
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

        private static Parser<MissingFieldLiteMessage> parser() {
            Parser<MissingFieldLiteMessage> result = parser;
            if (result == null) {
                synchronized (MissingFieldLiteMessage.class) {
                    result = parser;
                    if (result == null) {
                        result = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                        parser = result;
                    }
                }
            }
            return result;
        }

        public static final class Builder
                extends GeneratedMessageLite.Builder<MissingFieldLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
