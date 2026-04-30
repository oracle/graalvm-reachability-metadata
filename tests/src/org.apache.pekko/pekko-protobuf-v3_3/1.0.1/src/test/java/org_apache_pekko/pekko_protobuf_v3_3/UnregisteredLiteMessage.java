/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3;

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite;
import org.apache.pekko.protobufv3.internal.GeneratedMessageLite.MethodToInvoke;
import org.apache.pekko.protobufv3.internal.Parser;

public final class UnregisteredLiteMessage
        extends GeneratedMessageLite<UnregisteredLiteMessage, UnregisteredLiteMessage.Builder> {
    private static final UnregisteredLiteMessage DEFAULT_INSTANCE = new UnregisteredLiteMessage();
    private static volatile Parser<UnregisteredLiteMessage> parser;
    private String activeName = "pekko";

    private UnregisteredLiteMessage() {
    }

    public static UnregisteredLiteMessage getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public String getActiveName() {
        return activeName;
    }

    public boolean hasActiveName() {
        return true;
    }

    public void setActiveName(String activeName) {
        this.activeName = activeName;
    }

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case NEW_MUTABLE_INSTANCE:
                return new UnregisteredLiteMessage();
            case NEW_BUILDER:
                return new Builder();
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", new Object[] {});
            case GET_DEFAULT_INSTANCE:
                return DEFAULT_INSTANCE;
            case GET_PARSER:
                Parser<UnregisteredLiteMessage> localParser = parser;
                if (localParser == null) {
                    synchronized (UnregisteredLiteMessage.class) {
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
            extends GeneratedMessageLite.Builder<UnregisteredLiteMessage, Builder> {
        private Builder() {
            super(DEFAULT_INSTANCE);
        }
    }
}
