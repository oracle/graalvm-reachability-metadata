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

public final class DefaultInstanceLiteMessage
        extends GeneratedMessageLite<DefaultInstanceLiteMessage, DefaultInstanceLiteMessage.Builder> {
    public static final DefaultInstanceLiteMessage DEFAULT_INSTANCE;
    private static volatile Parser<DefaultInstanceLiteMessage> parser;

    static {
        DEFAULT_INSTANCE = new DefaultInstanceLiteMessage();
        registerDefaultInstance(DefaultInstanceLiteMessage.class, DEFAULT_INSTANCE);
    }

    private DefaultInstanceLiteMessage() {
    }

    public static DefaultInstanceLiteMessage getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    protected Object writeReplace() throws ObjectStreamException {
        return GeneratedMessageLite.SerializedForm.of(this);
    }

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case NEW_MUTABLE_INSTANCE:
                return new DefaultInstanceLiteMessage();
            case NEW_BUILDER:
                return new Builder();
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", new Object[] {});
            case GET_DEFAULT_INSTANCE:
                return DEFAULT_INSTANCE;
            case GET_PARSER:
                Parser<DefaultInstanceLiteMessage> localParser = parser;
                if (localParser == null) {
                    synchronized (DefaultInstanceLiteMessage.class) {
                        localParser = parser;
                        if (localParser == null) {
                            localParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
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
            extends GeneratedMessageLite.Builder<DefaultInstanceLiteMessage, Builder> {
        private Builder() {
            super(DEFAULT_INSTANCE);
        }
    }
}
