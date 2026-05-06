/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3;

import akka.protobufv3.internal.GeneratedMessageLite;
import akka.protobufv3.internal.Parser;

public final class MessageSchemaTestMessages {
    private MessageSchemaTestMessages() {
    }

    public static int serializedSizeForValidField() {
        return ValidRawMessageInfoMessage.defaultInstance().getSerializedSize();
    }

    public static int serializedSizeForMissingField() {
        return MissingRawMessageInfoFieldMessage.defaultInstance().getSerializedSize();
    }
}

final class ValidRawMessageInfoMessage extends GeneratedMessageLite<
        ValidRawMessageInfoMessage, ValidRawMessageInfoMessage.Builder> {
    private static final String ONE_INT32_FIELD_INFO =
            "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004";
    private static final ValidRawMessageInfoMessage DEFAULT_INSTANCE;
    private static volatile Parser<ValidRawMessageInfoMessage> PARSER;

    // Checkstyle: stop field name check
    private int existingField_;
    // Checkstyle: resume field name check

    static {
        DEFAULT_INSTANCE = new ValidRawMessageInfoMessage();
        registerDefaultInstance(ValidRawMessageInfoMessage.class, DEFAULT_INSTANCE);
    }

    private ValidRawMessageInfoMessage() {
    }

    static ValidRawMessageInfoMessage defaultInstance() {
        return DEFAULT_INSTANCE;
    }

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case NEW_MUTABLE_INSTANCE:
                return new ValidRawMessageInfoMessage();
            case NEW_BUILDER:
                return new Builder();
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(DEFAULT_INSTANCE, ONE_INT32_FIELD_INFO, new Object[] {"existingField_"});
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

    private static Parser<ValidRawMessageInfoMessage> parser() {
        Parser<ValidRawMessageInfoMessage> result = PARSER;
        if (result == null) {
            synchronized (ValidRawMessageInfoMessage.class) {
                result = PARSER;
                if (result == null) {
                    result = new GeneratedMessageLite.DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                    PARSER = result;
                }
            }
        }
        return result;
    }

    static final class Builder extends GeneratedMessageLite.Builder<ValidRawMessageInfoMessage, Builder> {
        private Builder() {
            super(DEFAULT_INSTANCE);
        }
    }
}

final class MissingRawMessageInfoFieldMessage extends GeneratedMessageLite<
        MissingRawMessageInfoFieldMessage, MissingRawMessageInfoFieldMessage.Builder> {
    private static final String ONE_INT32_FIELD_INFO =
            "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004";
    private static final MissingRawMessageInfoFieldMessage DEFAULT_INSTANCE;
    private static volatile Parser<MissingRawMessageInfoFieldMessage> PARSER;

    // Checkstyle: stop field name check
    private int existingField_;
    // Checkstyle: resume field name check

    static {
        DEFAULT_INSTANCE = new MissingRawMessageInfoFieldMessage();
        registerDefaultInstance(MissingRawMessageInfoFieldMessage.class, DEFAULT_INSTANCE);
    }

    private MissingRawMessageInfoFieldMessage() {
    }

    static MissingRawMessageInfoFieldMessage defaultInstance() {
        return DEFAULT_INSTANCE;
    }

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case NEW_MUTABLE_INSTANCE:
                return new MissingRawMessageInfoFieldMessage();
            case NEW_BUILDER:
                return new Builder();
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(DEFAULT_INSTANCE, ONE_INT32_FIELD_INFO, new Object[] {"missingField_"});
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

    private static Parser<MissingRawMessageInfoFieldMessage> parser() {
        Parser<MissingRawMessageInfoFieldMessage> result = PARSER;
        if (result == null) {
            synchronized (MissingRawMessageInfoFieldMessage.class) {
                result = PARSER;
                if (result == null) {
                    result = new GeneratedMessageLite.DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                    PARSER = result;
                }
            }
        }
        return result;
    }

    static final class Builder extends GeneratedMessageLite.Builder<MissingRawMessageInfoFieldMessage, Builder> {
        private Builder() {
            super(DEFAULT_INSTANCE);
        }
    }
}
