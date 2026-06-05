/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite.MethodToInvoke;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageLiteTest {

    @Test
    void toStringUsesGeneratedMessageLiteReflectionHelperForPublicAccessors() {
        ReflectiveLiteMessage message = ReflectiveLiteMessage.of(42);

        assertThat(message.toString()).contains("value: 42");
    }

    @SuppressWarnings("serial")
    public static final class ReflectiveLiteMessage extends GeneratedMessageLite<
            ReflectiveLiteMessage, ReflectiveLiteMessage.Builder> {
        private static final ReflectiveLiteMessage DEFAULT_INSTANCE = new ReflectiveLiteMessage();

        static {
            registerDefaultInstance(ReflectiveLiteMessage.class, DEFAULT_INSTANCE);
        }

        private static volatile Parser<ReflectiveLiteMessage> parser;
        private int value;

        private ReflectiveLiteMessage() {
        }

        private static ReflectiveLiteMessage of(int value) {
            ReflectiveLiteMessage message = new ReflectiveLiteMessage();
            message.value = value;
            return message;
        }

        public boolean hasValue() {
            return true;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        @Override
        protected Object dynamicMethod(
                MethodToInvoke method, Object firstArgument, Object secondArgument) {
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
                    return parser();
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException("Unsupported method: " + method);
            }
        }

        private static Parser<ReflectiveLiteMessage> parser() {
            Parser<ReflectiveLiteMessage> result = parser;
            if (result == null) {
                synchronized (ReflectiveLiteMessage.class) {
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
                extends GeneratedMessageLite.Builder<ReflectiveLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
