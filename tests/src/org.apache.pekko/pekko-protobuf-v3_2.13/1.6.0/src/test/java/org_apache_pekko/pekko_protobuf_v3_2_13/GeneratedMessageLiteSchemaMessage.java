/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13;

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite;
import org.apache.pekko.protobufv3.internal.Parser;

public final class GeneratedMessageLiteSchemaMessage
        extends GeneratedMessageLite<GeneratedMessageLiteSchemaMessage,
                GeneratedMessageLiteSchemaMessage.Builder> {
    private static final GeneratedMessageLiteSchemaMessage DEFAULT_INSTANCE =
            new GeneratedMessageLiteSchemaMessage();
    private static volatile Parser<GeneratedMessageLiteSchemaMessage> parser;

    static {
        registerDefaultInstance(GeneratedMessageLiteSchemaMessage.class, DEFAULT_INSTANCE);
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
                return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", new Object[0]);
            case NEW_MUTABLE_INSTANCE:
                return new GeneratedMessageLiteSchemaMessage();
            case NEW_BUILDER:
                return new Builder();
            case GET_DEFAULT_INSTANCE:
                return DEFAULT_INSTANCE;
            case GET_PARSER:
                Parser<GeneratedMessageLiteSchemaMessage> localParser = parser;
                if (localParser == null) {
                    synchronized (GeneratedMessageLiteSchemaMessage.class) {
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
            extends GeneratedMessageLite.Builder<GeneratedMessageLiteSchemaMessage, Builder> {
        private Builder() {
            super(DEFAULT_INSTANCE);
        }
    }
}
