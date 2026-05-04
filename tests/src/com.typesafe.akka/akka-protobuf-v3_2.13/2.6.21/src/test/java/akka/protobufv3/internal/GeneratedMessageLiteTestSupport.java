/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package akka.protobufv3.internal;

import java.lang.reflect.Method;

public final class GeneratedMessageLiteTestSupport {
    private GeneratedMessageLiteTestSupport() {
    }

    public static boolean lookupDefaultInstanceInitializesGeneratedMessageClass() {
        GeneratedMessageLite<?, ?> defaultInstance =
                GeneratedMessageLite.getDefaultInstance(UninitializedDefaultInstanceMessage.class);
        return defaultInstance == UninitializedDefaultInstanceMessage.defaultInstance();
    }

    public static String invokePublicAccessorThroughGeneratedMessageLite() {
        Method method = GeneratedMessageLite.getMethodOrDie(GeneratedMessageLiteTestSupport.class, "publicAccessor");
        return (String) GeneratedMessageLite.invokeOrDie(method, null);
    }

    public static String publicAccessor() {
        return "invoked through GeneratedMessageLite";
    }
}

final class UninitializedDefaultInstanceMessage extends GeneratedMessageLite<
        UninitializedDefaultInstanceMessage, UninitializedDefaultInstanceMessage.Builder> {
    private static final UninitializedDefaultInstanceMessage DEFAULT_INSTANCE;
    private static volatile Parser<UninitializedDefaultInstanceMessage> PARSER;

    static {
        DEFAULT_INSTANCE = new UninitializedDefaultInstanceMessage();
        registerDefaultInstance(UninitializedDefaultInstanceMessage.class, DEFAULT_INSTANCE);
    }

    private UninitializedDefaultInstanceMessage() {
    }

    static UninitializedDefaultInstanceMessage defaultInstance() {
        return DEFAULT_INSTANCE;
    }

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
        switch (method) {
            case NEW_MUTABLE_INSTANCE:
                return new UninitializedDefaultInstanceMessage();
            case NEW_BUILDER:
                return new Builder();
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", new Object[0]);
            case GET_DEFAULT_INSTANCE:
                return DEFAULT_INSTANCE;
            case GET_PARSER:
                return parser();
            case GET_MEMOIZED_IS_INITIALIZED:
                return (byte) 1;
            case SET_MEMOIZED_IS_INITIALIZED:
                return null;
            default:
                throw new UnsupportedOperationException("Unsupported GeneratedMessageLite method: " + method);
        }
    }

    private static Parser<UninitializedDefaultInstanceMessage> parser() {
        Parser<UninitializedDefaultInstanceMessage> result = PARSER;
        if (result == null) {
            synchronized (UninitializedDefaultInstanceMessage.class) {
                result = PARSER;
                if (result == null) {
                    result = new GeneratedMessageLite.DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                    PARSER = result;
                }
            }
        }
        return result;
    }

    static final class Builder extends GeneratedMessageLite.Builder<UninitializedDefaultInstanceMessage, Builder> {
        private Builder() {
            super(DEFAULT_INSTANCE);
        }
    }
}
