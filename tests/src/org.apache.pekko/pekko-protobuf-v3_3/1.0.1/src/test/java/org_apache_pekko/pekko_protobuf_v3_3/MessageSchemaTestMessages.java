/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3;

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite;
import org.apache.pekko.protobufv3.internal.GeneratedMessageLite.MethodToInvoke;

final class MessageSchemaFieldBackedLiteMessage
        extends GeneratedMessageLite<MessageSchemaFieldBackedLiteMessage, MessageSchemaFieldBackedLiteMessage.Builder> {
    private static final MessageSchemaFieldBackedLiteMessage DEFAULT_INSTANCE = new MessageSchemaFieldBackedLiteMessage(7);

    private int count_;

    private MessageSchemaFieldBackedLiteMessage(int count) {
        count_ = count;
    }

    static MessageSchemaFieldBackedLiteMessage getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    int getCount() {
        return count_;
    }

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case NEW_MUTABLE_INSTANCE:
                return new MessageSchemaFieldBackedLiteMessage(0);
            case NEW_BUILDER:
                return new Builder();
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(
                        DEFAULT_INSTANCE,
                        "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004",
                        new Object[] {"count_"});
            case GET_DEFAULT_INSTANCE:
                return DEFAULT_INSTANCE;
            case GET_PARSER:
                return null;
            case GET_MEMOIZED_IS_INITIALIZED:
                return (byte) 1;
            case SET_MEMOIZED_IS_INITIALIZED:
                return null;
            default:
                throw new UnsupportedOperationException();
        }
    }

    static final class Builder extends GeneratedMessageLite.Builder<MessageSchemaFieldBackedLiteMessage, Builder> {
        private Builder() {
            super(DEFAULT_INSTANCE);
        }
    }
}

final class MessageSchemaMissingFieldLiteMessage
        extends GeneratedMessageLite<MessageSchemaMissingFieldLiteMessage, MessageSchemaMissingFieldLiteMessage.Builder> {
    private static final MessageSchemaMissingFieldLiteMessage DEFAULT_INSTANCE = new MessageSchemaMissingFieldLiteMessage();

    private int present_ = 11;

    private MessageSchemaMissingFieldLiteMessage() {
    }

    static MessageSchemaMissingFieldLiteMessage getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    int getPresent() {
        return present_;
    }

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case NEW_MUTABLE_INSTANCE:
                return new MessageSchemaMissingFieldLiteMessage();
            case NEW_BUILDER:
                return new Builder();
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(
                        DEFAULT_INSTANCE,
                        "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0004",
                        new Object[] {"missing_"});
            case GET_DEFAULT_INSTANCE:
                return DEFAULT_INSTANCE;
            case GET_PARSER:
                return null;
            case GET_MEMOIZED_IS_INITIALIZED:
                return (byte) 1;
            case SET_MEMOIZED_IS_INITIALIZED:
                return null;
            default:
                throw new UnsupportedOperationException();
        }
    }

    static final class Builder extends GeneratedMessageLite.Builder<MessageSchemaMissingFieldLiteMessage, Builder> {
        private Builder() {
            super(DEFAULT_INSTANCE);
        }
    }
}
