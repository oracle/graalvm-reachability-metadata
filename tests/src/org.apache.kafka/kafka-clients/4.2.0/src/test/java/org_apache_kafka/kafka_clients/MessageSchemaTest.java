/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

public class MessageSchemaTest {
    @Test
    void reportsUnavailableGeneratedFieldWhenBuildingLiteSchema() {
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                BrokenGeneratedFieldMessage.getDefaultInstance()::getSerializedSize);

        assertTrue(exception.getMessage().contains("missingField_"));
    }

    public static final class BrokenGeneratedFieldMessage extends GeneratedMessageLite<
            BrokenGeneratedFieldMessage,
            BrokenGeneratedFieldMessage.Builder> {
        private static final BrokenGeneratedFieldMessage DEFAULT_INSTANCE = new BrokenGeneratedFieldMessage();
        private static volatile Parser<BrokenGeneratedFieldMessage> parser;

        @SuppressWarnings("java:S116")
        private int presentField_;

        static {
            registerDefaultInstance(BrokenGeneratedFieldMessage.class, DEFAULT_INSTANCE);
        }

        private BrokenGeneratedFieldMessage() {
        }

        static BrokenGeneratedFieldMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new BrokenGeneratedFieldMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(
                            DEFAULT_INSTANCE,
                            "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004",
                            new Object[] {"missingField_"});
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    return parser();
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException("Unknown method: " + method);
            }
        }

        private static Parser<BrokenGeneratedFieldMessage> parser() {
            Parser<BrokenGeneratedFieldMessage> localParser = parser;
            if (localParser == null) {
                synchronized (BrokenGeneratedFieldMessage.class) {
                    localParser = parser;
                    if (localParser == null) {
                        localParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                        parser = localParser;
                    }
                }
            }
            return localParser;
        }

        public static final class Builder extends GeneratedMessageLite.Builder<BrokenGeneratedFieldMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
