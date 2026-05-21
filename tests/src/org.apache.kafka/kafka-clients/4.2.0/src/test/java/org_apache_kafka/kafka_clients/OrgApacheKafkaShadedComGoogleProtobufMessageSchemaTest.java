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
    void rawLiteMessageSchemaReflectsDeclaredFieldByName() throws Exception {
        RawLiteMessage message = RawLiteMessage.parseFrom(new byte[] {8, 42});

        assertThat(message.getValue()).isEqualTo(42);
        assertThat(message.getSerializedSize()).isEqualTo(2);
    }

    @Test
    void rawLiteMessageSchemaReportsKnownFieldsWhenConfiguredFieldIsAbsent() {
        assertThatThrownBy(BrokenRawLiteMessage::parseEmpty)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Field missingValue_")
                .hasMessageContaining(BrokenRawLiteMessage.class.getName())
                .hasMessageContaining("Known fields are");
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class RawLiteMessage extends GeneratedMessageLite<RawLiteMessage, RawLiteMessage.Builder> {
        private static final RawLiteMessage DEFAULT_INSTANCE = new RawLiteMessage();
        private static volatile Parser<RawLiteMessage> PARSER;

        private int value_;

        static {
            registerDefaultInstance(RawLiteMessage.class, DEFAULT_INSTANCE);
        }

        private RawLiteMessage() {
        }

        public static RawLiteMessage parseFrom(byte[] data) throws InvalidProtocolBufferException {
            return parseFrom(DEFAULT_INSTANCE, data);
        }

        public int getValue() {
            return value_;
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
                    Parser<RawLiteMessage> localParser = PARSER;
                    if (localParser == null) {
                        localParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                        PARSER = localParser;
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
        }
    }

    public static final class BrokenRawLiteMessage
            extends GeneratedMessageLite<BrokenRawLiteMessage, BrokenRawLiteMessage.Builder> {
        private static final BrokenRawLiteMessage DEFAULT_INSTANCE = new BrokenRawLiteMessage();
        private static volatile Parser<BrokenRawLiteMessage> PARSER;

        static {
            registerDefaultInstance(BrokenRawLiteMessage.class, DEFAULT_INSTANCE);
        }

        private BrokenRawLiteMessage() {
        }

        public static BrokenRawLiteMessage parseEmpty() throws InvalidProtocolBufferException {
            return parseFrom(DEFAULT_INSTANCE, new byte[0]);
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object argument0, Object argument1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new BrokenRawLiteMessage();
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
                    Parser<BrokenRawLiteMessage> localParser = PARSER;
                    if (localParser == null) {
                        localParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                        PARSER = localParser;
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

        public static final class Builder extends GeneratedMessageLite.Builder<BrokenRawLiteMessage, Builder> {

            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
