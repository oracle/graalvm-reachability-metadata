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

public class OrgApacheKafkaShadedComGoogleProtobufExtensionSchemasTest {

    @Test
    void computesProto2LiteSchemaThroughGeneratedMessageApi() {
        Proto2LiteMessage message = Proto2LiteMessage.getDefaultInstance();

        assertThat(message.getSerializedSize()).isZero();
        assertThat(message.isInitialized()).isTrue();
    }

    public static final class Proto2LiteMessage
            extends GeneratedMessageLite<Proto2LiteMessage, Proto2LiteMessage.Builder> {
        private static final Proto2LiteMessage DEFAULT_INSTANCE = new Proto2LiteMessage();
        private static volatile Parser<Proto2LiteMessage> parser;

        static {
            DEFAULT_INSTANCE.makeImmutable();
            registerDefaultInstance(Proto2LiteMessage.class, DEFAULT_INSTANCE);
        }

        private Proto2LiteMessage() {
        }

        public static Proto2LiteMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object argument0, Object argument1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new Proto2LiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0001\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<Proto2LiteMessage> localParser = parser;
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

        public static final class Builder extends GeneratedMessageLite.Builder<Proto2LiteMessage, Builder> {

            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
