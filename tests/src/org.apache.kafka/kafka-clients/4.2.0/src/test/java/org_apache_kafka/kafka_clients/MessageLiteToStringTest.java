/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.kafka.shaded.com.google.protobuf.ByteString;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

public class MessageLiteToStringTest {
    @Test
    void printsGeneratedMessageLiteFieldsUsingPublicToString() {
        PrintableLiteMessage message = PrintableLiteMessage.withDisplayName("dynamic access");

        String printed = message.toString();

        assertTrue(printed.startsWith("# "));
        assertTrue(printed.contains("display_name: \"dynamic access\""));
    }

    public static final class PrintableLiteMessage extends GeneratedMessageLite<
            PrintableLiteMessage,
            PrintableLiteMessage.Builder> {
        private static final PrintableLiteMessage DEFAULT_INSTANCE = new PrintableLiteMessage();
        private static volatile Parser<PrintableLiteMessage> parser;

        private String displayName = "";

        static {
            registerDefaultInstance(PrintableLiteMessage.class, DEFAULT_INSTANCE);
        }

        private PrintableLiteMessage() {
        }

        private static PrintableLiteMessage withDisplayName(String displayName) {
            PrintableLiteMessage message = new PrintableLiteMessage();
            message.setDisplayName(displayName);
            return message;
        }

        public boolean hasDisplayName() {
            return !displayName.isEmpty();
        }

        public String getDisplayName() {
            return displayName;
        }

        public ByteString getDisplayNameBytes() {
            return ByteString.copyFromUtf8(displayName);
        }

        private void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
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
                    return parser();
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException("Unknown method: " + method);
            }
        }

        private static Parser<PrintableLiteMessage> parser() {
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
        }

        public static final class Builder extends GeneratedMessageLite.Builder<PrintableLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
