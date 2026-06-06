/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.GeneratedMessageLite.MethodToInvoke;
import com.google.protobuf.Parser;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

public class UnsafeUtilTest {
    @Test
    void directBufferInputUsesProtobufUnsafeSupport() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(Integer.BYTES);
        buffer.put(new byte[] {0x78, 0x56, 0x34, 0x12});
        buffer.flip();

        CodedInputStream input = CodedInputStream.newInstance(buffer);

        assertThat(input.readRawLittleEndian32()).isEqualTo(0x12345678);
        assertThat(input.isAtEnd()).isTrue();
    }

    @Test
    void unregisteredLiteMessageSchemaCreationUsesDefaultInstanceFallback() {
        UnregisteredLiteMessage message = new UnregisteredLiteMessage();

        assertThat(message.getSerializedSize()).isZero();
        assertThat(message.isInitialized()).isTrue();
    }

    private static final class UnregisteredLiteMessage extends GeneratedMessageLite<
            UnregisteredLiteMessage, UnregisteredLiteMessage.Builder> {
        private static final UnregisteredLiteMessage DEFAULT_INSTANCE = new UnregisteredLiteMessage();
        private static volatile Parser<UnregisteredLiteMessage> parser;

        private UnregisteredLiteMessage() {
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object firstArgument, Object secondArgument) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new UnregisteredLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    return parser();
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException("Unsupported method: " + method);
            }
        }

        private static Parser<UnregisteredLiteMessage> parser() {
            Parser<UnregisteredLiteMessage> result = parser;
            if (result == null) {
                synchronized (UnregisteredLiteMessage.class) {
                    result = parser;
                    if (result == null) {
                        result = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                        parser = result;
                    }
                }
            }
            return result;
        }

        private static final class Builder extends GeneratedMessageLite.Builder<UnregisteredLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
