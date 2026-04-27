/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

public class MessageSchemaTest {

    @Test
    void schemaInitializationReflectsDeclaredFieldForGeneratedLiteMetadata() {
        assertThatNoException().isThrownBy(() -> ValidLiteMessage.getDefaultInstance().hashCode());
    }

    @Test
    void schemaInitializationReportsKnownDeclaredFieldsWhenMetadataPointsAtMissingField() {
        assertThatThrownBy(() -> MalformedLiteMessage.getDefaultInstance().hashCode())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Field missing_")
            .hasMessageContaining("label_");
    }

    public static final class ValidLiteMessage
        extends GeneratedMessageLite<ValidLiteMessage, ValidLiteMessage.Builder> {
        private static final String MESSAGE_INFO =
            "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0208";
        private static final ValidLiteMessage DEFAULT_INSTANCE;
        private static volatile Parser<ValidLiteMessage> parser;

        private String label_ = "";

        static {
            ValidLiteMessage defaultInstance = new ValidLiteMessage();
            DEFAULT_INSTANCE = defaultInstance;
            registerDefaultInstance(ValidLiteMessage.class, defaultInstance);
        }

        public static ValidLiteMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new ValidLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, MESSAGE_INFO, new Object[] {"label_"});
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<ValidLiteMessage> resolvedParser = parser;
                    if (resolvedParser == null) {
                        synchronized (ValidLiteMessage.class) {
                            resolvedParser = parser;
                            if (resolvedParser == null) {
                                resolvedParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                                parser = resolvedParser;
                            }
                        }
                    }
                    return resolvedParser;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        public static final class Builder extends GeneratedMessageLite.Builder<ValidLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }

    public static final class MalformedLiteMessage
        extends GeneratedMessageLite<MalformedLiteMessage, MalformedLiteMessage.Builder> {
        private static final String MESSAGE_INFO =
            "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0208";
        private static final MalformedLiteMessage DEFAULT_INSTANCE;
        private static volatile Parser<MalformedLiteMessage> parser;

        private String label_ = "";

        static {
            MalformedLiteMessage defaultInstance = new MalformedLiteMessage();
            DEFAULT_INSTANCE = defaultInstance;
            registerDefaultInstance(MalformedLiteMessage.class, defaultInstance);
        }

        public static MalformedLiteMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new MalformedLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, MESSAGE_INFO, new Object[] {"missing_"});
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<MalformedLiteMessage> resolvedParser = parser;
                    if (resolvedParser == null) {
                        synchronized (MalformedLiteMessage.class) {
                            resolvedParser = parser;
                            if (resolvedParser == null) {
                                resolvedParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                                parser = resolvedParser;
                            }
                        }
                    }
                    return resolvedParser;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        public static final class Builder extends GeneratedMessageLite.Builder<MalformedLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
