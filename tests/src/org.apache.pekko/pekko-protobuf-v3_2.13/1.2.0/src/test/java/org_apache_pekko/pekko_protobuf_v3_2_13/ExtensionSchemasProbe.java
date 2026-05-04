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

public final class ExtensionSchemasProbe {
    private ExtensionSchemasProbe() {
    }

    public static Proto2LiteMessage parseProto2LiteMessage() throws InvalidProtocolBufferException {
        return Proto2LiteMessage.parseFrom(new byte[0]);
    }

    public static final class Proto2LiteMessage extends GeneratedMessageLite<Proto2LiteMessage, Proto2LiteMessage.Builder> {
        private static final Proto2LiteMessage DEFAULT_INSTANCE;
        private static volatile Parser<Proto2LiteMessage> parser;

        static {
            Proto2LiteMessage defaultInstance = new Proto2LiteMessage();
            DEFAULT_INSTANCE = defaultInstance;
            registerDefaultInstance(Proto2LiteMessage.class, defaultInstance);
        }

        private Proto2LiteMessage() {
        }

        public static Proto2LiteMessage parseFrom(byte[] data) throws InvalidProtocolBufferException {
            return parseFrom(DEFAULT_INSTANCE, data);
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new Proto2LiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, proto2RawMessageInfo(), new Object[0]);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<Proto2LiteMessage> currentParser = parser;
                    if (currentParser == null) {
                        synchronized (Proto2LiteMessage.class) {
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

        public static final class Builder extends GeneratedMessageLite.Builder<Proto2LiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }

    private static String proto2RawMessageInfo() {
        return "\u0001\u0000";
    }
}
