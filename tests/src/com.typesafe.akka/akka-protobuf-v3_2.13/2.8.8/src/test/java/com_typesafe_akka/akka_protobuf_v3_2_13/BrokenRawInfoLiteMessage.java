/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13;

import akka.protobufv3.internal.GeneratedMessageLite;
import akka.protobufv3.internal.GeneratedMessageLite.MethodToInvoke;

public final class BrokenRawInfoLiteMessage extends GeneratedMessageLite<
        BrokenRawInfoLiteMessage, BrokenRawInfoLiteMessage.Builder> {
    private static final String MALFORMED_FIELD_INFO = new String(
            new char[] {0, 1, 0, 0, 1, 1, 1, 0, 0, 0, 1, 4}
    );
    private static final BrokenRawInfoLiteMessage DEFAULT_INSTANCE;
    private int value_;

    static {
        DEFAULT_INSTANCE = new BrokenRawInfoLiteMessage();
        registerDefaultInstance(BrokenRawInfoLiteMessage.class, DEFAULT_INSTANCE);
    }

    private BrokenRawInfoLiteMessage() {
    }

    public static BrokenRawInfoLiteMessage defaultInstance() {
        return DEFAULT_INSTANCE;
    }

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object firstArgument, Object secondArgument) {
        switch (method) {
            case NEW_MUTABLE_INSTANCE:
                return new BrokenRawInfoLiteMessage();
            case NEW_BUILDER:
                return new Builder();
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(DEFAULT_INSTANCE, MALFORMED_FIELD_INFO, new Object[] {"missing_"});
            case GET_DEFAULT_INSTANCE:
                return DEFAULT_INSTANCE;
            case GET_PARSER:
                return null;
            case GET_MEMOIZED_IS_INITIALIZED:
                return (byte) 1;
            case SET_MEMOIZED_IS_INITIALIZED:
                return null;
            default:
                throw new UnsupportedOperationException("Unsupported GeneratedMessageLite method: " + method);
        }
    }

    public static final class Builder extends GeneratedMessageLite.Builder<BrokenRawInfoLiteMessage, Builder> {
        private Builder() {
            super(DEFAULT_INSTANCE);
        }
    }
}
