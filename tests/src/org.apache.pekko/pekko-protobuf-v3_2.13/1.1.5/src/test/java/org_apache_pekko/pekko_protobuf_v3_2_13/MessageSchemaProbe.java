/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13;

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite;
import org.apache.pekko.protobufv3.internal.InvalidProtocolBufferException;
import org.apache.pekko.protobufv3.internal.Parser;

public final class MessageSchemaProbe {
    private MessageSchemaProbe() {
    }

    public static int parseValidMessageValue() throws InvalidProtocolBufferException {
        return ValidMessage.parseFrom(new byte[0]).getValue();
    }

    public static RuntimeException parseInvalidMessageWithMissingField() {
        try {
            InvalidMessage.parseFrom(new byte[0]);
            throw new AssertionError("Expected schema creation to fail for a missing backing field");
        } catch (RuntimeException e) {
            return e;
        } catch (InvalidProtocolBufferException e) {
            throw new AssertionError("Schema creation should fail before parsing completes", e);
        }
    }

    public static final class ValidMessage extends GeneratedMessageLite<ValidMessage, ValidMessage.Builder> {
        private static final ValidMessage DEFAULT_INSTANCE;
        private static volatile Parser<ValidMessage> parser;

        private int value_;

        static {
            ValidMessage defaultInstance = new ValidMessage();
            DEFAULT_INSTANCE = defaultInstance;
            registerDefaultInstance(ValidMessage.class, defaultInstance);
        }

        private ValidMessage() {
        }

        public int getValue() {
            return value_;
        }

        public static ValidMessage parseFrom(byte[] data) throws InvalidProtocolBufferException {
            return parseFrom(DEFAULT_INSTANCE, data);
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new ValidMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, rawMessageInfo(), new Object[] {"value_"});
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<ValidMessage> currentParser = parser;
                    if (currentParser == null) {
                        synchronized (ValidMessage.class) {
                            currentParser = parser;
                            if (currentParser == null) {
                                currentParser = new GeneratedMessageLite.DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                                parser = currentParser;
                            }
                        }
                    }
                    return currentParser;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        public static final class Builder extends GeneratedMessageLite.Builder<ValidMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }

    public static final class InvalidMessage extends GeneratedMessageLite<InvalidMessage, InvalidMessage.Builder> {
        private static final InvalidMessage DEFAULT_INSTANCE;
        private static volatile Parser<InvalidMessage> parser;

        private int value_;

        static {
            InvalidMessage defaultInstance = new InvalidMessage();
            DEFAULT_INSTANCE = defaultInstance;
            registerDefaultInstance(InvalidMessage.class, defaultInstance);
        }

        private InvalidMessage() {
        }

        public static InvalidMessage parseFrom(byte[] data) throws InvalidProtocolBufferException {
            return parseFrom(DEFAULT_INSTANCE, data);
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new InvalidMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, rawMessageInfo(), new Object[] {"missing_"});
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<InvalidMessage> currentParser = parser;
                    if (currentParser == null) {
                        synchronized (InvalidMessage.class) {
                            currentParser = parser;
                            if (currentParser == null) {
                                currentParser = new GeneratedMessageLite.DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                                parser = currentParser;
                            }
                        }
                    }
                    return currentParser;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        public static final class Builder extends GeneratedMessageLite.Builder<InvalidMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }

    private static String rawMessageInfo() {
        return "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004";
    }
}
