/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package libcore.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.kafka.shaded.com.google.protobuf.CodedOutputStream;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite.DefaultInstanceBasedParser;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite.MethodToInvoke;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

public class UnsafeUtilTest {
    @Test
    void writesProtobufPayloadUsingUnsafeArraySupport() throws Exception {
        assertEquals("libcore.io.Memory", Memory.class.getName());

        String value = "unsafe-util-coverage";
        byte[] payload = new byte[] {1, 2, 3, 4, 5};
        byte[] buffer = new byte[128];
        CodedOutputStream output = CodedOutputStream.newInstance(buffer);

        output.writeStringNoTag(value);
        output.writeByteArrayNoTag(payload);
        output.flush();

        int expectedSize = CodedOutputStream.computeStringSizeNoTag(value)
                + CodedOutputStream.computeByteArraySizeNoTag(payload);
        assertEquals(expectedSize, output.getTotalBytesWritten());
        assertTrue(output.spaceLeft() > 0);
    }

    @Test
    void buildsSchemaForUnregisteredGeneratedMessageLite() {
        MinimalLiteMessage message = MinimalLiteMessage.getDefaultInstance();

        assertEquals(0, message.getSerializedSize());
        assertEquals(0, message.toByteArray().length);
    }

    private static final class MinimalLiteMessage
            extends GeneratedMessageLite<MinimalLiteMessage, MinimalLiteMessage.Builder> {
        private static final MinimalLiteMessage DEFAULT_INSTANCE = new MinimalLiteMessage();
        private static volatile Parser<MinimalLiteMessage> parser;

        private MinimalLiteMessage() {
        }

        private static MinimalLiteMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object firstArgument, Object secondArgument) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new MinimalLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", new Object[0]);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<MinimalLiteMessage> localParser = parser;
                    if (localParser == null) {
                        synchronized (MinimalLiteMessage.class) {
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
                    throw new UnsupportedOperationException("Unsupported method: " + method);
            }
        }

        private static final class Builder extends GeneratedMessageLite.Builder<MinimalLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}

final class Memory {
    private Memory() {
    }

    public static long peekLong(long address, boolean swap) {
        throw unsupported();
    }

    public static long peekLong(int address, boolean swap) {
        throw unsupported();
    }

    public static void pokeLong(long address, long value, boolean swap) {
        throw unsupported();
    }

    public static void pokeLong(int address, long value, boolean swap) {
        throw unsupported();
    }

    public static void pokeInt(long address, int value, boolean swap) {
        throw unsupported();
    }

    public static void pokeInt(int address, int value, boolean swap) {
        throw unsupported();
    }

    public static int peekInt(long address, boolean swap) {
        throw unsupported();
    }

    public static int peekInt(int address, boolean swap) {
        throw unsupported();
    }

    public static void pokeByte(long address, byte value) {
        throw unsupported();
    }

    public static void pokeByte(int address, byte value) {
        throw unsupported();
    }

    public static byte peekByte(long address) {
        throw unsupported();
    }

    public static byte peekByte(int address) {
        throw unsupported();
    }

    public static void pokeByteArray(long address, byte[] bytes, int offset, int count) {
        throw unsupported();
    }

    public static void pokeByteArray(int address, byte[] bytes, int offset, int count) {
        throw unsupported();
    }

    public static void peekByteArray(long address, byte[] bytes, int offset, int count) {
        throw unsupported();
    }

    public static void peekByteArray(int address, byte[] bytes, int offset, int count) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Memory stubs are only present for protobuf capability checks");
    }
}
