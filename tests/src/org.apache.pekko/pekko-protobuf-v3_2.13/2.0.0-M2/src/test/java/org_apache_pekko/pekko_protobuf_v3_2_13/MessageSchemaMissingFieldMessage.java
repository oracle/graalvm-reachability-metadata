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

public final class MessageSchemaMissingFieldMessage
        extends GeneratedMessageLite<MessageSchemaMissingFieldMessage,
                MessageSchemaMissingFieldMessage.Builder> {
    private static final MessageSchemaMissingFieldMessage DEFAULT_INSTANCE =
            new MessageSchemaMissingFieldMessage();
    private static volatile Parser<MessageSchemaMissingFieldMessage> parser;

    @SuppressWarnings("unused")
    private int present_;

    static {
        registerDefaultInstance(MessageSchemaMissingFieldMessage.class, DEFAULT_INSTANCE);
    }

    public static MessageSchemaMissingFieldMessage parseFrom(byte[] data)
            throws InvalidProtocolBufferException {
        return parseFrom(DEFAULT_INSTANCE, data);
    }

    @Override
    protected Object dynamicMethod(
            MethodToInvoke method,
            Object firstArgument,
            Object secondArgument) {
        switch (method) {
            case GET_MEMOIZED_IS_INITIALIZED:
                return (byte) 1;
            case SET_MEMOIZED_IS_INITIALIZED:
                return null;
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(
                        DEFAULT_INSTANCE,
                        "\u0000\u0002\u0000\u0000\u0001\u0002\u0002\u0000\u0000\u0000"
                                + "\u0001\u0004\u0002\u0004",
                        new Object[] {"present_", "missing_"}
                );
            case NEW_MUTABLE_INSTANCE:
                return new MessageSchemaMissingFieldMessage();
            case NEW_BUILDER:
                return new Builder();
            case GET_DEFAULT_INSTANCE:
                return DEFAULT_INSTANCE;
            case GET_PARSER:
                Parser<MessageSchemaMissingFieldMessage> localParser = parser;
                if (localParser == null) {
                    synchronized (MessageSchemaMissingFieldMessage.class) {
                        localParser = parser;
                        if (localParser == null) {
                            localParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                            parser = localParser;
                        }
                    }
                }
                return localParser;
            default:
                throw new UnsupportedOperationException(
                        "Dynamic operation is not needed for this coverage test"
                );
        }
    }

    public static final class Builder
            extends GeneratedMessageLite.Builder<MessageSchemaMissingFieldMessage, Builder> {
        private Builder() {
            super(DEFAULT_INSTANCE);
        }
    }
}
