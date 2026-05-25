/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

public class ExtensionSchemasTest {
    @Test
    void parsesExtendableProto2LiteMessageWithOptionalFullRuntimeSchema() throws Exception {
        ExtendableProto2Message parsed = ExtendableProto2Message.parseFrom(
                new byte[0], ExtensionRegistryLite.getEmptyRegistry());

        assertThat(parsed).isEqualTo(ExtendableProto2Message.getDefaultInstance());
    }

    public static final class ExtendableProto2Message
            extends GeneratedMessageLite.ExtendableMessage<
                    ExtendableProto2Message, ExtendableProto2Message.Builder> {
        private static final ExtendableProto2Message DEFAULT_INSTANCE;
        private static volatile Parser<ExtendableProto2Message> parser;

        static {
            ExtendableProto2Message defaultInstance = new ExtendableProto2Message();
            DEFAULT_INSTANCE = defaultInstance;
            registerDefaultInstance(ExtendableProto2Message.class, defaultInstance);
        }

        private ExtendableProto2Message() {
        }

        public static ExtendableProto2Message parseFrom(
                byte[] data,
                ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
            return parseFrom(DEFAULT_INSTANCE, data, extensionRegistry);
        }

        public static ExtendableProto2Message getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        @SuppressWarnings({"unchecked", "fallthrough"})
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new ExtendableProto2Message();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0001\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<ExtendableProto2Message> localParser = parser;
                    if (localParser == null) {
                        synchronized (ExtendableProto2Message.class) {
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

        public static final class Builder extends GeneratedMessageLite.ExtendableBuilder<
                ExtendableProto2Message, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
