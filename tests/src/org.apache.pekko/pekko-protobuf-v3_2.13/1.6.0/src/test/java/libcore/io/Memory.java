/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package libcore.io;

public final class Memory {
    private Memory() {
        // Utility class.
    }

    public static long peekLong(long address, boolean swap) {
        return 0L;
    }

    public static long peekLong(int address, boolean swap) {
        return 0L;
    }

    public static void pokeLong(long address, long value, boolean swap) {
        // UnsafeUtil only verifies that this Android API method exists.
    }

    public static void pokeLong(int address, long value, boolean swap) {
        // UnsafeUtil only verifies that this Android API method exists.
    }

    public static void pokeInt(long address, int value, boolean swap) {
        // UnsafeUtil only verifies that this Android API method exists.
    }

    public static void pokeInt(int address, int value, boolean swap) {
        // UnsafeUtil only verifies that this Android API method exists.
    }

    public static int peekInt(long address, boolean swap) {
        return 0;
    }

    public static int peekInt(int address, boolean swap) {
        return 0;
    }

    public static void pokeByte(long address, byte value) {
        // UnsafeUtil only verifies that this Android API method exists.
    }

    public static void pokeByte(int address, byte value) {
        // UnsafeUtil only verifies that this Android API method exists.
    }

    public static byte peekByte(long address) {
        return 0;
    }

    public static byte peekByte(int address) {
        return 0;
    }

    public static void pokeByteArray(long address, byte[] source, int offset, int count) {
        // UnsafeUtil only verifies that this Android API method exists.
    }

    public static void pokeByteArray(int address, byte[] source, int offset, int count) {
        // UnsafeUtil only verifies that this Android API method exists.
    }

    public static void peekByteArray(long address, byte[] destination, int offset, int count) {
        // UnsafeUtil only verifies that this Android API method exists.
    }

    public static void peekByteArray(int address, byte[] destination, int offset, int count) {
        // UnsafeUtil only verifies that this Android API method exists.
    }
}
