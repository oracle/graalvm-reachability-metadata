/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import com.google.protobuf.AbstractParser;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MessageSchemaTest {
    @Test
    void malformedGeneratedMessageInfoReportsKnownFields() {
        MalformedFieldMessage message = MalformedFieldMessage.getDefaultInstance();

        assertThatThrownBy(message::getSerializedSize)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Field missing_ for ")
                .hasMessageContaining("not found. Known fields are");
    }

    private static final class MalformedFieldMessage
            extends GeneratedMessageLite<MalformedFieldMessage, MalformedFieldMessage.Builder> {
        private static final MalformedFieldMessage DEFAULT_INSTANCE = new MalformedFieldMessage();

        @SuppressWarnings("unused")
        private int present_;

        static {
            registerDefaultInstance(MalformedFieldMessage.class, DEFAULT_INSTANCE);
        }

        static MalformedFieldMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new MalformedFieldMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(
                            DEFAULT_INSTANCE,
                            "\u0000\u0001\u0000\u0000\u0001\u0001"
                                    + "\u0001\u0000\u0000\u0000\u0001\u0004",
                            new Object[] {"missing_"});
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    return new AbstractParser<MalformedFieldMessage>() {
                        @Override
                        public MalformedFieldMessage parsePartialFrom(
                                CodedInputStream input, ExtensionRegistryLite extensionRegistry)
                                throws InvalidProtocolBufferException {
                            return MalformedFieldMessage.getDefaultInstance();
                        }
                    };
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        private static final class Builder
                extends GeneratedMessageLite.Builder<MalformedFieldMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
