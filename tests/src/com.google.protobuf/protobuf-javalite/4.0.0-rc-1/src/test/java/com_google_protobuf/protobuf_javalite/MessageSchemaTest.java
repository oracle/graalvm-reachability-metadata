/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

public class MessageSchemaTest {
    @Test
    void reportsAvailableFieldsWhenRawMessageInfoNamesUnknownField() {
        RuntimeException exception = InvalidFieldMessage.parseEmptyInput();

        assertThat(exception)
                .hasMessageContaining("unknown_")
                .hasMessageContaining(InvalidFieldMessage.class.getName())
                .hasMessageContaining("Known fields");
    }

    private static final class InvalidFieldMessage extends GeneratedMessageLite<
            InvalidFieldMessage, InvalidFieldMessage.Builder> {
        private static final InvalidFieldMessage DEFAULT_INSTANCE = new InvalidFieldMessage();
        private static volatile Parser<InvalidFieldMessage> parser;

        private int value_;

        static {
            registerDefaultInstance(InvalidFieldMessage.class, DEFAULT_INSTANCE);
        }

        private InvalidFieldMessage() {
        }

        private static RuntimeException parseEmptyInput() {
            try {
                parseFrom(DEFAULT_INSTANCE, new byte[0]);
                throw new AssertionError("Expected schema creation to reject unknown field name");
            } catch (RuntimeException e) {
                return e;
            } catch (InvalidProtocolBufferException e) {
                throw new AssertionError("Schema creation should complete before input parsing", e);
            }
        }

        @Override
        protected Object dynamicMethod(
                MethodToInvoke method, Object firstArgument, Object secondArgument) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new InvalidFieldMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(
                            DEFAULT_INSTANCE, rawMessageInfo(), new Object[] {"unknown_"});
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

        private static Parser<InvalidFieldMessage> parser() {
            Parser<InvalidFieldMessage> result = parser;
            if (result == null) {
                synchronized (InvalidFieldMessage.class) {
                    result = parser;
                    if (result == null) {
                        result = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                        parser = result;
                    }
                }
            }
            return result;
        }

        private static String rawMessageInfo() {
            return "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004";
        }

        private static final class Builder
                extends GeneratedMessageLite.Builder<InvalidFieldMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
