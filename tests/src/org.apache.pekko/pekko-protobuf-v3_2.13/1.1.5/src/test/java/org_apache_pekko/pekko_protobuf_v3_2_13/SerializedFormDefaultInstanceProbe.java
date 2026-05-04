/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13;

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite;
import org.apache.pekko.protobufv3.internal.Parser;

public final class SerializedFormDefaultInstanceProbe extends GeneratedMessageLite<
        SerializedFormDefaultInstanceProbe,
        SerializedFormDefaultInstanceProbe.Builder> {
    private static final SerializedFormDefaultInstanceProbe DEFAULT_INSTANCE;
    private static volatile Parser<SerializedFormDefaultInstanceProbe> parser;

    static {
        DEFAULT_INSTANCE = new SerializedFormDefaultInstanceProbe();
        registerDefaultInstance(SerializedFormDefaultInstanceProbe.class, DEFAULT_INSTANCE);
    }

    private SerializedFormDefaultInstanceProbe() {
    }

    public static SerializedFormDefaultInstanceProbe getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case NEW_MUTABLE_INSTANCE:
                return new SerializedFormDefaultInstanceProbe();
            case NEW_BUILDER:
                return new Builder();
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", null);
            case GET_DEFAULT_INSTANCE:
                return DEFAULT_INSTANCE;
            case GET_PARSER:
                Parser<SerializedFormDefaultInstanceProbe> result = parser;
                if (result == null) {
                    synchronized (SerializedFormDefaultInstanceProbe.class) {
                        result = parser;
                        if (result == null) {
                            result = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                            parser = result;
                        }
                    }
                }
                return result;
            case GET_MEMOIZED_IS_INITIALIZED:
                return (byte) 1;
            case SET_MEMOIZED_IS_INITIALIZED:
                return null;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public static final class Builder extends GeneratedMessageLite.Builder<
            SerializedFormDefaultInstanceProbe,
            Builder> {
        private Builder() {
            super(DEFAULT_INSTANCE);
        }
    }
}

final class SerializedFormLegacyDefaultInstanceProbe extends GeneratedMessageLite<
        SerializedFormLegacyDefaultInstanceProbe,
        SerializedFormLegacyDefaultInstanceProbe.Builder> {
    // Checkstyle: stop constant name check
    private static final SerializedFormLegacyDefaultInstanceProbe defaultInstance;
    // Checkstyle: resume constant name check
    private static volatile Parser<SerializedFormLegacyDefaultInstanceProbe> parser;

    static {
        defaultInstance = new SerializedFormLegacyDefaultInstanceProbe();
        registerDefaultInstance(SerializedFormLegacyDefaultInstanceProbe.class, defaultInstance);
    }

    private SerializedFormLegacyDefaultInstanceProbe() {
    }

    static SerializedFormLegacyDefaultInstanceProbe getDefaultInstance() {
        return defaultInstance;
    }

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case NEW_MUTABLE_INSTANCE:
                return new SerializedFormLegacyDefaultInstanceProbe();
            case NEW_BUILDER:
                return new Builder();
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(defaultInstance, "\u0000\u0000", null);
            case GET_DEFAULT_INSTANCE:
                return defaultInstance;
            case GET_PARSER:
                Parser<SerializedFormLegacyDefaultInstanceProbe> result = parser;
                if (result == null) {
                    synchronized (SerializedFormLegacyDefaultInstanceProbe.class) {
                        result = parser;
                        if (result == null) {
                            result = new DefaultInstanceBasedParser<>(defaultInstance);
                            parser = result;
                        }
                    }
                }
                return result;
            case GET_MEMOIZED_IS_INITIALIZED:
                return (byte) 1;
            case SET_MEMOIZED_IS_INITIALIZED:
                return null;
            default:
                throw new UnsupportedOperationException();
        }
    }

    static final class Builder extends GeneratedMessageLite.Builder<
            SerializedFormLegacyDefaultInstanceProbe,
            Builder> {
        private Builder() {
            super(defaultInstance);
        }
    }
}
