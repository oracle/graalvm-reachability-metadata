/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.nio.ByteBuffer;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class Cleaner0Test {
    @Test
    void freesDirectByteBufferThroughPlatformDependent() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(16);
        buffer.putLong(0x0102030405060708L);
        buffer.flip();

        Assertions.assertEquals(0x0102030405060708L, buffer.getLong());
        Assertions.assertDoesNotThrow(() -> PlatformDependent.freeDirectBuffer(buffer));
    }
}
