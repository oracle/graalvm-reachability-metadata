/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package libcore.io;

public final class Memory {
    private Memory() {
    }

    public static long peekLong(long address, boolean swap) {
        throw new UnsupportedOperationException("Test Android memory stub does not read native memory");
    }

    public static long peekLong(int address, boolean swap) {
        throw new UnsupportedOperationException("Test Android memory stub does not read native memory");
    }

    public static void pokeLong(long address, long value, boolean swap) {
        throw new UnsupportedOperationException("Test Android memory stub does not write native memory");
    }

    public static void pokeLong(int address, long value, boolean swap) {
        throw new UnsupportedOperationException("Test Android memory stub does not write native memory");
    }

    public static int peekInt(long address, boolean swap) {
        throw new UnsupportedOperationException("Test Android memory stub does not read native memory");
    }

    public static int peekInt(int address, boolean swap) {
        throw new UnsupportedOperationException("Test Android memory stub does not read native memory");
    }

    public static void pokeInt(long address, int value, boolean swap) {
        throw new UnsupportedOperationException("Test Android memory stub does not write native memory");
    }

    public static void pokeInt(int address, int value, boolean swap) {
        throw new UnsupportedOperationException("Test Android memory stub does not write native memory");
    }

    public static byte peekByte(long address) {
        throw new UnsupportedOperationException("Test Android memory stub does not read native memory");
    }

    public static byte peekByte(int address) {
        throw new UnsupportedOperationException("Test Android memory stub does not read native memory");
    }

    public static void pokeByte(long address, byte value) {
        throw new UnsupportedOperationException("Test Android memory stub does not write native memory");
    }

    public static void pokeByte(int address, byte value) {
        throw new UnsupportedOperationException("Test Android memory stub does not write native memory");
    }

    public static void peekByteArray(long address, byte[] destination, int offset, int count) {
        throw new UnsupportedOperationException("Test Android memory stub does not read native memory");
    }

    public static void peekByteArray(int address, byte[] destination, int offset, int count) {
        throw new UnsupportedOperationException("Test Android memory stub does not read native memory");
    }

    public static void pokeByteArray(long address, byte[] source, int offset, int count) {
        throw new UnsupportedOperationException("Test Android memory stub does not write native memory");
    }

    public static void pokeByteArray(int address, byte[] source, int offset, int count) {
        throw new UnsupportedOperationException("Test Android memory stub does not write native memory");
    }
}
