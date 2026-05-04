/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13;

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite;
import org.apache.pekko.protobufv3.internal.Parser;

public final class MessageLiteToStringProbe
        extends GeneratedMessageLite<MessageLiteToStringProbe, MessageLiteToStringProbe.Builder> {
    private static final MessageLiteToStringProbe DEFAULT_INSTANCE;
    private static volatile Parser<MessageLiteToStringProbe> parser;

    private final String name;

    static {
        DEFAULT_INSTANCE = new MessageLiteToStringProbe("");
        registerDefaultInstance(MessageLiteToStringProbe.class, DEFAULT_INSTANCE);
    }

    private MessageLiteToStringProbe(String name) {
        this.name = name;
    }

    public static String print(String name) {
        return new MessageLiteToStringProbe(name).toString();
    }

    public String getName() {
        return name;
    }

    public boolean hasName() {
        return !name.isEmpty();
    }

    public void setName(String unused) {
        throw new UnsupportedOperationException("MessageLiteToString only checks for this setter's presence");
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case GET_MEMOIZED_IS_INITIALIZED:
                return (byte) 1;
            case SET_MEMOIZED_IS_INITIALIZED:
                return null;
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", null);
            case NEW_MUTABLE_INSTANCE:
                return new MessageLiteToStringProbe("");
            case NEW_BUILDER:
                return new Builder();
            case GET_DEFAULT_INSTANCE:
                return DEFAULT_INSTANCE;
            case GET_PARSER:
                Parser<MessageLiteToStringProbe> localParser = parser;
                if (localParser == null) {
                    synchronized (MessageLiteToStringProbe.class) {
                        localParser = parser;
                        if (localParser == null) {
                            localParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                            parser = localParser;
                        }
                    }
                }
                return localParser;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public static final class Builder extends GeneratedMessageLite.Builder<MessageLiteToStringProbe, Builder> {
        private Builder() {
            super(DEFAULT_INSTANCE);
        }
    }
}
