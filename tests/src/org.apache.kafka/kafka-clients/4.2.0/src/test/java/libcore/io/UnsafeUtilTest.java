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
