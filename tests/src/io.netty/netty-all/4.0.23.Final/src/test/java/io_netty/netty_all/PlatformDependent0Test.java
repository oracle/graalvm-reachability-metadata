/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PlatformDependent0Test {
    @Test
    void directBufferAllocationDiscoversPlatformUnsafeCapabilities() {
        ByteBuf buffer = Unpooled.directBuffer(Long.BYTES);
        try {
            Assertions.assertTrue(buffer.isDirect());

            long value = 0x0102030405060708L;
            buffer.writeLong(value);

            Assertions.assertEquals(value, buffer.readLong());
            Assertions.assertEquals(PlatformDependent.hasUnsafe(), buffer.hasMemoryAddress());
            if (PlatformDependent.hasUnsafe()) {
                Assertions.assertNotEquals(0L, buffer.memoryAddress());
            } else {
                Assertions.assertFalse(PlatformDependent.directBufferPreferred());
            }
        } finally {
            buffer.release();
        }

        Assertions.assertTrue(PlatformDependent.maxDirectMemory() > 0);
    }
}
