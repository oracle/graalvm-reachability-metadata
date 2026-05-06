/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3;

import akka.protobufv3.internal.GeneratedMessageLite;
import akka.protobufv3.internal.Parser;

public final class MessageSchemaRawMessageInfo {
    static final String SINGLE_PROTO3_INT32_FIELD_INFO = encodeRawMessageInfo(
            0, // flags: proto3, non-message-set
            1, // field count
            0, // oneof count
            0, // has-bits count
            1, // minimum field number
            1, // maximum field number
            1, // field entry count
            0, // map field count
            0, // repeated field count
            0, // check-initialized field count
            1, // field number
            4  // FieldType.INT32
    );

    private MessageSchemaRawMessageInfo() {
    }

    private static String encodeRawMessageInfo(int... values) {
        StringBuilder builder = new StringBuilder(values.length);
        for (int value : values) {
            builder.append((char) value);
        }
        return builder.toString();
    }
}

final class MessageSchemaInt32ProbeMessage
        extends GeneratedMessageLite<MessageSchemaInt32ProbeMessage, MessageSchemaInt32ProbeMessageBuilder> {
    private static final MessageSchemaInt32ProbeMessage DEFAULT_INSTANCE = new MessageSchemaInt32ProbeMessage();
    private static final Parser<MessageSchemaInt32ProbeMessage> PARSER =
            new GeneratedMessageLite.DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);

    static {
        registerDefaultInstance(MessageSchemaInt32ProbeMessage.class, DEFAULT_INSTANCE);
    }

    @SuppressWarnings("unused")
    private int value_;

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case GET_MEMOIZED_IS_INITIALIZED:
                return (byte) 1;
            case SET_MEMOIZED_IS_INITIALIZED:
                return null;
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(
                        DEFAULT_INSTANCE,
                        MessageSchemaRawMessageInfo.SINGLE_PROTO3_INT32_FIELD_INFO,
                        new Object[] {"value_" }
                );
            case NEW_MUTABLE_INSTANCE:
                return new MessageSchemaInt32ProbeMessage();
            case NEW_BUILDER:
                return new MessageSchemaInt32ProbeMessageBuilder();
            case GET_DEFAULT_INSTANCE:
                return DEFAULT_INSTANCE;
            case GET_PARSER:
                return PARSER;
            default:
                throw new UnsupportedOperationException("Unknown method: " + method);
        }
    }
}

final class MessageSchemaInt32ProbeMessageBuilder
        extends GeneratedMessageLite.Builder<MessageSchemaInt32ProbeMessage, MessageSchemaInt32ProbeMessageBuilder> {
    MessageSchemaInt32ProbeMessageBuilder() {
        super(new MessageSchemaInt32ProbeMessage());
    }
}

final class MessageSchemaMissingFieldProbeMessage
        extends GeneratedMessageLite<MessageSchemaMissingFieldProbeMessage, MessageSchemaMissingFieldProbeMessageBuilder> {
    private static final MessageSchemaMissingFieldProbeMessage DEFAULT_INSTANCE = new MessageSchemaMissingFieldProbeMessage();
    private static final Parser<MessageSchemaMissingFieldProbeMessage> PARSER =
            new GeneratedMessageLite.DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);

    static {
        registerDefaultInstance(MessageSchemaMissingFieldProbeMessage.class, DEFAULT_INSTANCE);
    }

    @SuppressWarnings("unused")
    private int value_;

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case GET_MEMOIZED_IS_INITIALIZED:
                return (byte) 1;
            case SET_MEMOIZED_IS_INITIALIZED:
                return null;
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(
                        DEFAULT_INSTANCE,
                        MessageSchemaRawMessageInfo.SINGLE_PROTO3_INT32_FIELD_INFO,
                        new Object[] {"absent_" }
                );
            case NEW_MUTABLE_INSTANCE:
                return new MessageSchemaMissingFieldProbeMessage();
            case NEW_BUILDER:
                return new MessageSchemaMissingFieldProbeMessageBuilder();
            case GET_DEFAULT_INSTANCE:
                return DEFAULT_INSTANCE;
            case GET_PARSER:
                return PARSER;
            default:
                throw new UnsupportedOperationException("Unknown method: " + method);
        }
    }
}

final class MessageSchemaMissingFieldProbeMessageBuilder
        extends GeneratedMessageLite.Builder<MessageSchemaMissingFieldProbeMessage, MessageSchemaMissingFieldProbeMessageBuilder> {
    MessageSchemaMissingFieldProbeMessageBuilder() {
        super(new MessageSchemaMissingFieldProbeMessage());
    }
}
