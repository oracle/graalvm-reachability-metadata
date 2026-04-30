/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3;

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite;
import org.apache.pekko.protobufv3.internal.GeneratedMessageLite.DefaultInstanceBasedParser;
import org.apache.pekko.protobufv3.internal.GeneratedMessageLite.MethodToInvoke;
import org.apache.pekko.protobufv3.internal.Parser;

import java.io.ObjectStreamException;

public final class FallbackDefaultInstanceLiteMessage
        extends GeneratedMessageLite<FallbackDefaultInstanceLiteMessage, FallbackDefaultInstanceLiteMessage.Builder> {
    @SuppressWarnings("checkstyle:ConstantName")
    public static final FallbackDefaultInstanceLiteMessage defaultInstance;
    private static volatile Parser<FallbackDefaultInstanceLiteMessage> parser;

    static {
        defaultInstance = new FallbackDefaultInstanceLiteMessage();
        registerDefaultInstance(FallbackDefaultInstanceLiteMessage.class, defaultInstance);
    }

    private FallbackDefaultInstanceLiteMessage() {
    }

    public static FallbackDefaultInstanceLiteMessage getDefaultInstance() {
        return defaultInstance;
    }

    protected Object writeReplace() throws ObjectStreamException {
        return GeneratedMessageLite.SerializedForm.of(this);
    }

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case NEW_MUTABLE_INSTANCE:
                return new FallbackDefaultInstanceLiteMessage();
            case NEW_BUILDER:
                return new Builder();
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(defaultInstance, "\u0000\u0000", new Object[] {});
            case GET_DEFAULT_INSTANCE:
                return defaultInstance;
            case GET_PARSER:
                Parser<FallbackDefaultInstanceLiteMessage> localParser = parser;
                if (localParser == null) {
                    synchronized (FallbackDefaultInstanceLiteMessage.class) {
                        localParser = parser;
                        if (localParser == null) {
                            localParser = new DefaultInstanceBasedParser<>(defaultInstance);
                            parser = localParser;
                        }
                    }
                }
                return localParser;
            case GET_MEMOIZED_IS_INITIALIZED:
                return (byte) 1;
            case SET_MEMOIZED_IS_INITIALIZED:
                return null;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public static final class Builder
            extends GeneratedMessageLite.Builder<FallbackDefaultInstanceLiteMessage, Builder> {
        private Builder() {
            super(defaultInstance);
        }
    }
}
