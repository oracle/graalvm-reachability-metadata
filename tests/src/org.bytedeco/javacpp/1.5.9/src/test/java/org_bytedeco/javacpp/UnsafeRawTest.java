/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacpp;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.junit.jupiter.api.Test;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.indexer.ByteIndexer;

public class UnsafeRawTest {

    public static class AddressBytePointer extends BytePointer {
        public AddressBytePointer(long address, long capacity) {
            this.address = address;
            capacity(capacity);
        }
    }

    @Test
    public void rawIndexerReadsAndWritesNativePointerMemory() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment memory = arena.allocate(4);
            memory.set(ValueLayout.JAVA_BYTE, 0, (byte) 3);
            memory.set(ValueLayout.JAVA_BYTE, 1, (byte) 5);
            memory.set(ValueLayout.JAVA_BYTE, 2, (byte) 7);
            memory.set(ValueLayout.JAVA_BYTE, 3, (byte) 9);
            AddressBytePointer pointer = new AddressBytePointer(memory.address(), memory.byteSize());
            ByteIndexer indexer = ByteIndexer.create(pointer);
            try {
                assertThat(indexer.get(2)).isEqualTo((byte) 7);

                indexer.put(1, (byte) 11);

                assertThat(memory.get(ValueLayout.JAVA_BYTE, 1)).isEqualTo((byte) 11);
            } finally {
                indexer.release();
            }
        }
    }
}
