/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3;

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite;

public final class GeneratedMessageLiteSerializedFormFallbackProbe
        extends GeneratedMessageLite<
                GeneratedMessageLiteSerializedFormFallbackProbe,
                GeneratedMessageLiteSerializedFormFallbackProbe.Builder> {
    @SuppressWarnings("checkstyle:ConstantName")
    private static final GeneratedMessageLiteSerializedFormFallbackProbe defaultInstance =
            new GeneratedMessageLiteSerializedFormFallbackProbe();

    static {
        registerDefaultInstance(GeneratedMessageLiteSerializedFormFallbackProbe.class, defaultInstance);
    }

    private GeneratedMessageLiteSerializedFormFallbackProbe() {
    }

    public static GeneratedMessageLiteSerializedFormFallbackProbe getDefaultInstance() {
        return defaultInstance;
    }

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(defaultInstance, "\u0000\u0000", null);
            case NEW_MUTABLE_INSTANCE:
                return new GeneratedMessageLiteSerializedFormFallbackProbe();
            case NEW_BUILDER:
                return new Builder(defaultInstance);
            case GET_DEFAULT_INSTANCE:
                return defaultInstance;
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

    public static final class Builder
            extends GeneratedMessageLite.Builder<GeneratedMessageLiteSerializedFormFallbackProbe, Builder> {
        private Builder(GeneratedMessageLiteSerializedFormFallbackProbe defaultInstance) {
            super(defaultInstance);
        }
    }
}
