/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

public class MessageSchemaTest {
    @Test
    void schemaCreationUsesRawMessageInfoFieldNamesPresentOnTheMessageClass() {
        assertThatCode(() -> new MessageWithRawInt32Field().hashCode())
                .doesNotThrowAnyException();
    }

    @Test
    void schemaCreationReportsRawMessageInfoFieldNamesMissingFromTheMessageClass() {
        assertThatThrownBy(() -> new MessageWithMissingRawField().hashCode())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Field missing_")
                .hasMessageContaining(MessageWithMissingRawField.class.getName());
    }

    public static final class MessageWithRawInt32Field extends GeneratedMessageLite<
            MessageWithRawInt32Field,
            MessageWithRawInt32Field.Builder> {
        private static final MessageWithRawInt32Field DEFAULT_INSTANCE;
        private static volatile Parser<MessageWithRawInt32Field> parser;

        @SuppressWarnings("unused")
        private int value_;

        static {
            MessageWithRawInt32Field instance = new MessageWithRawInt32Field();
            DEFAULT_INSTANCE = instance;
            registerDefaultInstance(MessageWithRawInt32Field.class, instance);
        }

        private MessageWithRawInt32Field() {
        }

        @Override
        protected Object dynamicMethod(
                MethodToInvoke method,
                Object firstArgument,
                Object secondArgument
        ) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new MessageWithRawInt32Field();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(
                            DEFAULT_INSTANCE,
                            rawMessageInfoForOneInt32Field(),
                            new Object[] {"value_"}
                    );
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<MessageWithRawInt32Field> localParser = parser;
                    if (localParser == null) {
                        synchronized (MessageWithRawInt32Field.class) {
                            localParser = parser;
                            if (localParser == null) {
                                localParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                                parser = localParser;
                            }
                        }
                    }
                    return localParser;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException(
                            "Dynamic operation is not needed for this coverage test"
                    );
            }
        }

        private static String rawMessageInfoForOneInt32Field() {
            return "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004";
        }

        public static final class Builder extends GeneratedMessageLite.Builder<
                MessageWithRawInt32Field,
                Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }

    public static final class MessageWithMissingRawField extends GeneratedMessageLite<
            MessageWithMissingRawField,
            MessageWithMissingRawField.Builder> {
        private static final MessageWithMissingRawField DEFAULT_INSTANCE;
        private static volatile Parser<MessageWithMissingRawField> parser;

        static {
            MessageWithMissingRawField instance = new MessageWithMissingRawField();
            DEFAULT_INSTANCE = instance;
            registerDefaultInstance(MessageWithMissingRawField.class, instance);
        }

        private MessageWithMissingRawField() {
        }

        @Override
        protected Object dynamicMethod(
                MethodToInvoke method,
                Object firstArgument,
                Object secondArgument
        ) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new MessageWithMissingRawField();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(
                            DEFAULT_INSTANCE,
                            rawMessageInfoForOneInt32Field(),
                            new Object[] {"missing_"}
                    );
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<MessageWithMissingRawField> localParser = parser;
                    if (localParser == null) {
                        synchronized (MessageWithMissingRawField.class) {
                            localParser = parser;
                            if (localParser == null) {
                                localParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                                parser = localParser;
                            }
                        }
                    }
                    return localParser;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException(
                            "Dynamic operation is not needed for this coverage test"
                    );
            }
        }

        private static String rawMessageInfoForOneInt32Field() {
            return "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004";
        }

        public static final class Builder extends GeneratedMessageLite.Builder<
                MessageWithMissingRawField,
                Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
