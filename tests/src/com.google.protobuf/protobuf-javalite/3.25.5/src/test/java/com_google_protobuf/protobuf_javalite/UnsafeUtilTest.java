/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import com.google.protobuf.AbstractParser;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsafeUtilTest {
    @Test
    void byteStringAndDirectBufferParsingExerciseUnsafeInitialization() throws Exception {
        ByteString payload = ByteString.copyFromUtf8("unsafe-util coverage");
        assertThat(payload.toStringUtf8()).isEqualTo("unsafe-util coverage");

        ByteBuffer directBuffer = ByteBuffer.allocateDirect(4);
        directBuffer.put((byte) 0x08);
        directBuffer.put((byte) 0x96);
        directBuffer.put((byte) 0x01);
        directBuffer.put((byte) 0x00);
        directBuffer.flip();

        int tag = CodedInputStream.newInstance(directBuffer).readTag();
        assertThat(tag).isEqualTo(8);
    }

    @Test
    void unregisteredLiteMessageSchemaLookupUsesUnsafeAllocationFallback() {
        UnregisteredLiteMessage message = new UnregisteredLiteMessage();
        assertThat(message.getSerializedSize()).isZero();
        assertThat(message.toByteArray()).isEmpty();
    }

    private static final class UnregisteredLiteMessage
            extends GeneratedMessageLite<UnregisteredLiteMessage, UnregisteredLiteMessage.Builder> {
        private static final UnregisteredLiteMessage DEFAULT_INSTANCE = new UnregisteredLiteMessage();

        static {
            DEFAULT_INSTANCE.makeImmutable();
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new UnregisteredLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", new Object[0]);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    return new AbstractParser<UnregisteredLiteMessage>() {
                        @Override
                        public UnregisteredLiteMessage parsePartialFrom(
                                CodedInputStream input, ExtensionRegistryLite extensionRegistry)
                                throws InvalidProtocolBufferException {
                            return new UnregisteredLiteMessage();
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
                extends GeneratedMessageLite.Builder<UnregisteredLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
