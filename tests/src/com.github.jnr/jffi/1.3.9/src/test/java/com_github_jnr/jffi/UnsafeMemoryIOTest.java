/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jffi;

import com.kenai.jffi.MemoryIO;
import com.kenai.jffi.UnsafeMemoryIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class UnsafeMemoryIOTest {
    @Test
    void copiesAndFillsNativeMemoryWithTheOptimizedAccessor() {
        MemoryIO memory = MemoryIO.getInstance();
        assertInstanceOf(UnsafeMemoryIO.class, memory);
        long address = memory.allocateMemory(64, true);
        assertNotEquals(0L, address);
        try {
            byte[] payload = {3, 1, 4, 1, 5, 9};
            memory.putByteArray(address, payload, 0, payload.length);
            memory.copyMemory(address, address + 32, payload.length);
            memory.setMemory(address, payload.length, (byte) 0x2A);

            byte[] copied = new byte[payload.length];
            memory.getByteArray(address + 32, copied, 0, copied.length);
            assertArrayEquals(payload, copied);
            for (int index = 0; index < payload.length; index++) {
                assertEquals((byte) 0x2A, memory.getByte(address + index));
            }
        } finally {
            memory.freeMemory(address);
        }
    }
}
