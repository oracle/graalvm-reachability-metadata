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

public class ComKenaiJffiMemoryIOTest {
    @Test
    void readsAndWritesAllocatedNativeMemory() {
        System.clearProperty("jffi.memory.checked");
        System.clearProperty("jffi.unsafe.disabled");

        MemoryIO memory = MemoryIO.getInstance();
        long address = memory.allocateMemory(32, true);
        assertThat(address).isNotZero();

        try {
            assertThat(memory.getLong(address)).isZero();

            memory.putByte(address, (byte) 0x12);
            memory.putShort(address + 2, (short) 0x3456);
            memory.putInt(address + 4, 0x789ABCDE);
            memory.putLong(address + 8, 0x123456789ABCDEFL);
            memory.putFloat(address + 16, 1.25F);
            memory.putDouble(address + 24, 2.5D);

            assertThat(memory.getByte(address)).isEqualTo((byte) 0x12);
            assertThat(memory.getShort(address + 2)).isEqualTo((short) 0x3456);
            assertThat(memory.getInt(address + 4)).isEqualTo(0x789ABCDE);
            assertThat(memory.getLong(address + 8)).isEqualTo(0x123456789ABCDEFL);
            assertThat(memory.getFloat(address + 16)).isEqualTo(1.25F);
            assertThat(memory.getDouble(address + 24)).isEqualTo(2.5D);
        } finally {
            memory.freeMemory(address);
        }
    }
}
