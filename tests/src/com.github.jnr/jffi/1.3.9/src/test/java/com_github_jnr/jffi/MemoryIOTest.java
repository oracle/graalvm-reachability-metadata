/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jffi;

import com.kenai.jffi.MemoryIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class MemoryIOTest {
    @Test
    void readsAndWritesPrimitiveValuesAndPointers() {
        MemoryIO memory = MemoryIO.getInstance();
        long address = memory.allocateMemory(64, true);
        assertNotEquals(0L, address);
        try {
            assertEquals(0L, memory.getLong(address));
            memory.putByte(address, (byte) -9);
            memory.putShort(address + 8, (short) 12345);
            memory.putInt(address + 16, -123456789);
            memory.putLong(address + 24, 0x123456789ABCDEFL);
            memory.putFloat(address + 32, 1.25f);
            memory.putDouble(address + 40, -987.5);
            memory.putAddress(address + 48, address + 16);

            assertEquals((byte) -9, memory.getByte(address));
            assertEquals((short) 12345, memory.getShort(address + 8));
            assertEquals(-123456789, memory.getInt(address + 16));
            assertEquals(0x123456789ABCDEFL, memory.getLong(address + 24));
            assertEquals(1.25f, memory.getFloat(address + 32));
            assertEquals(-987.5, memory.getDouble(address + 40));
            assertEquals(address + 16, memory.getAddress(address + 48));
            assertEquals(-123456789, memory.getInt(memory.getAddress(address + 48)));
        } finally {
            memory.freeMemory(address);
        }
    }

    @Test
    void transfersArraySlicesBetweenHeapAndNativeMemory() {
        MemoryIO memory = MemoryIO.getInstance();
        long address = memory.allocateMemory(32, true);
        assertNotEquals(0L, address);
        try {
            int[] source = {99, 11, -22, 33, 88};
            memory.putIntArray(address, source, 1, 3);
            int[] result = {7, 0, 0, 0, 9};
            memory.getIntArray(address, result, 1, 3);
            assertArrayEquals(new int[]{7, 11, -22, 33, 9}, result);
            assertEquals(-22, memory.getInt(address + Integer.BYTES));
        } finally {
            memory.freeMemory(address);
        }
    }
}
