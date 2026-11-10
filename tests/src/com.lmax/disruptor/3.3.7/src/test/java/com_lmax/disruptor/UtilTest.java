/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lmax.disruptor;

import com.lmax.disruptor.util.Util;
import org.junit.jupiter.api.Test;

import java.lang.ref.Reference;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class UtilTest {
    @Test
    @SuppressWarnings("deprecation")
    void locatesDirectBufferMemoryAcrossViews() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(32);
        try {
            long address = Util.getAddressFromDirectByteBuffer(buffer);
            assertNotEquals(0L, address);

            buffer.position(8);
            ByteBuffer duplicate = buffer.duplicate();
            ByteBuffer slice = buffer.slice();
            assertEquals(address, Util.getAddressFromDirectByteBuffer(duplicate));
            assertEquals(address + 8, Util.getAddressFromDirectByteBuffer(slice));

            slice.putLong(0, 123456789L);
            assertEquals(123456789L, buffer.getLong(8));
        } finally {
            Reference.reachabilityFence(buffer);
        }
    }
}
