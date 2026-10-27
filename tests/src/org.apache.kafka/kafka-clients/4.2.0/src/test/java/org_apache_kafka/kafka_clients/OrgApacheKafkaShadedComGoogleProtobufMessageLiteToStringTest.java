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

public class OrgApacheKafkaShadedComGoogleProtobufMessageLiteToStringTest {

    @Test
    void formatsGeneratedLiteMessageThroughPublicToString() {
        String printed = PrintableLiteMessage.getDefaultInstance().toString();

        assertThat(printed).contains("# ");
    }

    public static final class PrintableLiteMessage
            extends GeneratedMessageLite<PrintableLiteMessage, PrintableLiteMessage.Builder> {
        private static final PrintableLiteMessage DEFAULT_INSTANCE = new PrintableLiteMessage();
        private static volatile Parser<PrintableLiteMessage> parser;

        static {
            DEFAULT_INSTANCE.makeImmutable();
            registerDefaultInstance(PrintableLiteMessage.class, DEFAULT_INSTANCE);
        }

        private PrintableLiteMessage() {
        }

        public static PrintableLiteMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object argument0, Object argument1) {
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
                    Parser<PrintableLiteMessage> localParser = parser;
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

        public static final class Builder extends GeneratedMessageLite.Builder<PrintableLiteMessage, Builder> {

            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
