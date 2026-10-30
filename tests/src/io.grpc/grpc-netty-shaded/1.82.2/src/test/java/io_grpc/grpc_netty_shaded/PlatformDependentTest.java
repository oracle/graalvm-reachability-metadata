/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.buffer.ByteBuf;
import io.grpc.netty.shaded.io.netty.buffer.ByteBufUtil;
import io.grpc.netty.shaded.io.netty.buffer.Unpooled;
import io.grpc.netty.shaded.io.netty.util.internal.PlatformDependent;

public class PlatformDependentTest {
    @Test
    void estimatesMemoryAndCopiesLargeBuffers() {
        assertTrue(PlatformDependent.estimateMaxDirectMemory() > 0);
        byte[] expected = new byte[4096];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) i;
        }
        ByteBuf buffer = Unpooled.directBuffer(expected.length);
        try {
            buffer.writeBytes(expected);
            assertArrayEquals(expected, ByteBufUtil.getBytes(buffer));
        } finally {
            buffer.release();
        }
    }
}
