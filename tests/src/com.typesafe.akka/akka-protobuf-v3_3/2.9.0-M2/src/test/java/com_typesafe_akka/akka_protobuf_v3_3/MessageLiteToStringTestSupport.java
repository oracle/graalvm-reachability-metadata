/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3;

import akka.protobufv3.internal.GeneratedMessageLite;
import akka.protobufv3.internal.Parser;
import java.util.List;
import java.util.Map;

public final class MessageLiteToStringTestSupport {
    private MessageLiteToStringTestSupport() {
    }

    public static String formatSampleMessage() {
        PrintableLiteMessage message = new PrintableLiteMessage(
                "native-image", List.of("reflection"), Map.of("declared_methods", 1));

        return message.toString();
    }

    public static final class PrintableLiteMessage extends GeneratedMessageLite<
            PrintableLiteMessage, PrintableLiteMessage.Builder> {
        private static final PrintableLiteMessage DEFAULT_INSTANCE = new PrintableLiteMessage("", List.of(), Map.of());
        private static volatile Parser<PrintableLiteMessage> PARSER;

        private final String name;
        private final List<String> tags;
        private final Map<String, Integer> counts;

        private PrintableLiteMessage(String name, List<String> tags, Map<String, Integer> counts) {
            this.name = name;
            this.tags = tags;
            this.counts = counts;
        }

        public String getName() {
            return name;
        }

        public boolean hasName() {
            return !name.isEmpty();
        }

        @SuppressWarnings("unused")
        private void setName(String value) {
            throw new UnsupportedOperationException("The sample message is immutable");
        }

        public List<String> getTagsList() {
            return tags;
        }

        public Map<String, Integer> getCountsMap() {
            return counts;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new PrintableLiteMessage("", List.of(), Map.of());
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", new Object[0]);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    return parser();
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException("Unsupported GeneratedMessageLite method: " + method);
            }
        }

        private static Parser<PrintableLiteMessage> parser() {
            Parser<PrintableLiteMessage> result = PARSER;
            if (result == null) {
                synchronized (PrintableLiteMessage.class) {
                    result = PARSER;
                    if (result == null) {
                        result = new GeneratedMessageLite.DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                        PARSER = result;
                    }
                }
            }
            return result;
        }

        public static final class Builder extends GeneratedMessageLite.Builder<PrintableLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
