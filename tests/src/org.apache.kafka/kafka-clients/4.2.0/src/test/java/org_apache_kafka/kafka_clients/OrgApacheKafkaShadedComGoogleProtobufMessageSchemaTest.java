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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OrgApacheKafkaShadedComGoogleProtobufMessageSchemaTest {

    @Test
    void initializesGeneratedLiteSchemaByReflectingDeclaredField() {
        ReflectiveLiteMessage message = ReflectiveLiteMessage.getDefaultInstance();

        assertThat(message.isInitialized()).isTrue();
        assertThat(message.getSerializedSize()).isZero();
    }

    @Test
    void reportsGeneratedLiteSchemaFieldNamesWhenDeclaredFieldLookupFails() {
        MissingFieldLiteMessage message = MissingFieldLiteMessage.getDefaultInstance();

        assertThatThrownBy(message::getSerializedSize)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Field missing_")
                .hasMessageContaining(MissingFieldLiteMessage.class.getName())
                .hasMessageContaining("value_");
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class ReflectiveLiteMessage
            extends GeneratedMessageLite<ReflectiveLiteMessage, ReflectiveLiteMessage.Builder> {
        private static final ReflectiveLiteMessage DEFAULT_INSTANCE = new ReflectiveLiteMessage();
        private static volatile Parser<ReflectiveLiteMessage> parser;
        private int value_;

        static {
            registerDefaultInstance(ReflectiveLiteMessage.class, DEFAULT_INSTANCE);
        }

        private ReflectiveLiteMessage() {
        }

        public static ReflectiveLiteMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object argument0, Object argument1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new ReflectiveLiteMessage();
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
                    Parser<ReflectiveLiteMessage> localParser = parser;
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

        public static final class Builder extends GeneratedMessageLite.Builder<ReflectiveLiteMessage, Builder> {

            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class MissingFieldLiteMessage
            extends GeneratedMessageLite<MissingFieldLiteMessage, MissingFieldLiteMessage.Builder> {
        private static final MissingFieldLiteMessage DEFAULT_INSTANCE = new MissingFieldLiteMessage();
        private static volatile Parser<MissingFieldLiteMessage> parser;
        private int value_;

        static {
            registerDefaultInstance(MissingFieldLiteMessage.class, DEFAULT_INSTANCE);
        }

        private MissingFieldLiteMessage() {
        }

        public static MissingFieldLiteMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object argument0, Object argument1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new MissingFieldLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(
                            DEFAULT_INSTANCE,
                            "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004",
                            new Object[] {"missing_"});
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<MissingFieldLiteMessage> localParser = parser;
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

        public static final class Builder extends GeneratedMessageLite.Builder<MissingFieldLiteMessage, Builder> {

            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
