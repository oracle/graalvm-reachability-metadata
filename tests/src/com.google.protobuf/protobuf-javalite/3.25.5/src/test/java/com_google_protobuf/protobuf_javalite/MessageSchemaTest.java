/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

public class MessageSchemaTest {
    @Test
    public void parserBuildsSchemaForDeclaredFieldBackedMessage() throws InvalidProtocolBufferException {
        DeclaredFieldMessage message = DeclaredFieldMessage.parseFrom(new byte[] {8, 42});

        assertEquals(42, message.getValue());
    }

    @Test
    public void parserReportsMissingMessageInfoFieldAfterScanningDeclaredFields() {
        assertThrows(RuntimeException.class, () -> MissingFieldMessage.parseFrom(new byte[0]));
    }

    public static final class DeclaredFieldMessage
            extends GeneratedMessageLite<DeclaredFieldMessage, DeclaredFieldMessageBuilder> {
        private static final DeclaredFieldMessage DEFAULT_INSTANCE = new DeclaredFieldMessage();
        private static volatile Parser<DeclaredFieldMessage> parser;

        static {
            registerDefaultInstance(DeclaredFieldMessage.class, DEFAULT_INSTANCE);
        }

        private int value_;

        private DeclaredFieldMessage() {
        }

        public int getValue() {
            return value_;
        }

        public static DeclaredFieldMessage parseFrom(byte[] data) throws InvalidProtocolBufferException {
            return GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, data);
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new DeclaredFieldMessage();
                case NEW_BUILDER:
                    return new DeclaredFieldMessageBuilder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(
                            DEFAULT_INSTANCE,
                            "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004",
                            new Object[] {"value_"});
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<DeclaredFieldMessage> result = parser;
                    if (result == null) {
                        synchronized (DeclaredFieldMessage.class) {
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
                    throw new UnsupportedOperationException();
            }
        }
    }

    public static final class DeclaredFieldMessageBuilder
            extends GeneratedMessageLite.Builder<DeclaredFieldMessage, DeclaredFieldMessageBuilder> {
        private DeclaredFieldMessageBuilder() {
            super(DeclaredFieldMessage.DEFAULT_INSTANCE);
        }
    }

    public static final class MissingFieldMessage
            extends GeneratedMessageLite<MissingFieldMessage, MissingFieldMessageBuilder> {
        private static final MissingFieldMessage DEFAULT_INSTANCE = new MissingFieldMessage();
        private static volatile Parser<MissingFieldMessage> parser;

        static {
            registerDefaultInstance(MissingFieldMessage.class, DEFAULT_INSTANCE);
        }

        private MissingFieldMessage() {
        }

        public static MissingFieldMessage parseFrom(byte[] data) throws InvalidProtocolBufferException {
            return GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, data);
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new MissingFieldMessage();
                case NEW_BUILDER:
                    return new MissingFieldMessageBuilder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(
                            DEFAULT_INSTANCE,
                            "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004",
                            new Object[] {"missing_"});
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<MissingFieldMessage> result = parser;
                    if (result == null) {
                        synchronized (MissingFieldMessage.class) {
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
                    throw new UnsupportedOperationException();
            }
        }
    }

    public static final class MissingFieldMessageBuilder
            extends GeneratedMessageLite.Builder<MissingFieldMessage, MissingFieldMessageBuilder> {
        private MissingFieldMessageBuilder() {
            super(MissingFieldMessage.DEFAULT_INSTANCE);
        }
    }
}
