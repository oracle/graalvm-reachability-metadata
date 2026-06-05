/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite.ExtendableBuilder;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite.ExtendableMessage;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite.GeneratedExtension;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.apache.kafka.shaded.com.google.protobuf.WireFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufExtensionSchemasTest {

    @Test
    void serializesLiteMessageWithExtensionThroughPublicGeneratedMessageApi() {
        ExtendableLiteMessage message = ExtendableLiteMessage.newBuilder()
                .setExtension(ExtendableLiteMessage.VALUE_EXTENSION, 42)
                .build();

        byte[] serialized = message.toByteArray();

        assertThat(message.hasExtension(ExtendableLiteMessage.VALUE_EXTENSION)).isTrue();
        assertThat(message.getExtension(ExtendableLiteMessage.VALUE_EXTENSION)).isEqualTo(42);
        assertThat(serialized).isNotEmpty();
    }

    @SuppressWarnings("serial")
    public static final class ExtendableLiteMessage
            extends ExtendableMessage<ExtendableLiteMessage, ExtendableLiteMessage.Builder> {
        private static final ExtendableLiteMessage DEFAULT_INSTANCE = new ExtendableLiteMessage();
        private static volatile Parser<ExtendableLiteMessage> parser;

        public static final GeneratedExtension<ExtendableLiteMessage, Integer> VALUE_EXTENSION =
                GeneratedMessageLite.newSingularGeneratedExtension(
                        DEFAULT_INSTANCE,
                        0,
                        null,
                        null,
                        100,
                        WireFormat.FieldType.INT32,
                        Integer.class);

        static {
            registerDefaultInstance(ExtendableLiteMessage.class, DEFAULT_INSTANCE);
        }

        private ExtendableLiteMessage() {
        }

        public static Builder newBuilder() {
            return DEFAULT_INSTANCE.createBuilder();
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object firstArgument, Object secondArgument) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new ExtendableLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0001\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<ExtendableLiteMessage> result = parser;
                    if (result == null) {
                        synchronized (ExtendableLiteMessage.class) {
                            result = parser;
                            if (result == null) {
                                result = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                                parser = result;
                            }
                        }
                    }
                    return result;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException("Unsupported method: " + method);
            }
        }

        public static final class Builder extends ExtendableBuilder<ExtendableLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
