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

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageLiteTest {

    @Test
    void printsLiteMessageFieldsThroughGeneratedMessageLiteToString() {
        LiteToStringMessage message = LiteToStringMessage.of(42);

        String textFormat = message.toString();

        assertThat(textFormat)
                .contains("# ")
                .contains("value: 42");
    }

    public static final class LiteToStringMessage
            extends GeneratedMessageLite<LiteToStringMessage, LiteToStringMessage.Builder> {

        private static final LiteToStringMessage DEFAULT_INSTANCE = new LiteToStringMessage(0);
        private static volatile Parser<LiteToStringMessage> parser;

        private final int value;

        private LiteToStringMessage(int value) {
            this.value = value;
        }

        public static LiteToStringMessage of(int value) {
            return new LiteToStringMessage(value);
        }

        public int getValue() {
            return value;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(value);
        }

        @SuppressWarnings("unused")
        private void setValue(int value) {
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object argument0, Object argument1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new LiteToStringMessage(0);
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(
                            DEFAULT_INSTANCE,
                            "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004",
                            null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<LiteToStringMessage> localParser = parser;
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

        public static final class Builder extends GeneratedMessageLite.Builder<LiteToStringMessage, Builder> {

            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
