/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.apache.kafka.shaded.com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OrgApacheKafkaShadedComGoogleProtobufMessageSchemaTest {

    @Test
    void serializesGeneratedLiteMessageThroughRawMessageInfoFields() throws Exception {
        RawLiteMessage message = RawLiteMessage.newBuilder()
                .setValue(42)
                .build();

        byte[] payload = message.toByteArray();
        RawLiteMessage parsed = RawLiteMessage.parseFrom(payload);

        assertThat(parsed.getValue()).isEqualTo(42);
        assertThat(parsed.getSerializedSize()).isEqualTo(payload.length);
    }

    @Test
    void reportsUnavailableRawMessageInfoFields() {
        assertThatThrownBy(() -> MissingFieldMessage.getDefaultInstance().getSerializedSize())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missingValue_")
                .hasMessageContaining(MissingFieldMessage.class.getName());
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class RawLiteMessage extends GeneratedMessageLite<RawLiteMessage, RawLiteMessage.Builder> {
        private static final RawLiteMessage DEFAULT_INSTANCE;
        private static volatile Parser<RawLiteMessage> parser;

        static {
            RawLiteMessage defaultInstance = new RawLiteMessage();
            DEFAULT_INSTANCE = defaultInstance;
            registerDefaultInstance(RawLiteMessage.class, defaultInstance);
        }

        private int value_;

        private RawLiteMessage() {
        }

        public static Builder newBuilder() {
            return DEFAULT_INSTANCE.createBuilder();
        }

        public static RawLiteMessage parseFrom(byte[] data) throws InvalidProtocolBufferException {
            return parseFrom(DEFAULT_INSTANCE, data);
        }

        public int getValue() {
            return value_;
        }

        private void setValue(int value) {
            value_ = value;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object argument0, Object argument1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new RawLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(
                            DEFAULT_INSTANCE,
                            "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004",
                            new Object[] {"value_"});
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<RawLiteMessage> localParser = parser;
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

        public static final class Builder extends GeneratedMessageLite.Builder<RawLiteMessage, Builder> {

            private Builder() {
                super(DEFAULT_INSTANCE);
            }

            public Builder setValue(int value) {
                copyOnWrite();
                instance.setValue(value);
                return this;
            }
        }
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class MissingFieldMessage
            extends GeneratedMessageLite<MissingFieldMessage, MissingFieldMessage.Builder> {
        private static final MissingFieldMessage DEFAULT_INSTANCE;
        private static volatile Parser<MissingFieldMessage> parser;

        static {
            MissingFieldMessage defaultInstance = new MissingFieldMessage();
            DEFAULT_INSTANCE = defaultInstance;
            registerDefaultInstance(MissingFieldMessage.class, defaultInstance);
        }

        @SuppressWarnings("unused")
        private int availableValue_;

        private MissingFieldMessage() {
        }

        public static MissingFieldMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object argument0, Object argument1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new MissingFieldMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(
                            DEFAULT_INSTANCE,
                            "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004",
                            new Object[] {"missingValue_"});
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<MissingFieldMessage> localParser = parser;
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

        public static final class Builder extends GeneratedMessageLite.Builder<MissingFieldMessage, Builder> {

            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
