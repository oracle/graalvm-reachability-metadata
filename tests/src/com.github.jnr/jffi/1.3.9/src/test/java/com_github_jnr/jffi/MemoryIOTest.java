/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jffi;

import com.kenai.jffi.MemoryIO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MemoryIOTest {
    @Test
    void readsAndWritesAllocatedNativeMemory() {
        MemoryIO memoryIO = MemoryIO.getInstance();
        long address = memoryIO.allocateMemory(32, true);
        assertThat(address).isNotZero();

        try {
            memoryIO.putByte(address, (byte) 0x5A);
            memoryIO.putShort(address + 2, (short) 0x1234);
            memoryIO.putInt(address + 4, 0x12345678);
            memoryIO.putLong(address + 8, 0x0102030405060708L);

            assertThat(memoryIO.getByte(address)).isEqualTo((byte) 0x5A);
            assertThat(memoryIO.getShort(address + 2)).isEqualTo((short) 0x1234);
            assertThat(memoryIO.getInt(address + 4)).isEqualTo(0x12345678);
            assertThat(memoryIO.getLong(address + 8)).isEqualTo(0x0102030405060708L);
        } finally {
            memoryIO.freeMemory(address);
        }
    }
}
