/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class PlatformDependent0Test {
    @Test
    void initializesUnsafePlatformSupportAndAccessesDirectMemory() {
        assertThat(PlatformDependent.javaVersion()).isGreaterThanOrEqualTo(6);

        boolean hasUnsafe = PlatformDependent.hasUnsafe();
        if (hasUnsafe) {
            ByteBuffer directBuffer = ByteBuffer.allocateDirect(Long.BYTES);
            long directBufferAddress = PlatformDependent.directBufferAddress(directBuffer);
            assertThat(directBufferAddress).isNotZero();

            long memoryAddress = PlatformDependent.allocateMemory(Long.BYTES);
            try {
                long expectedLongValue = 0x0102030405060708L;
                PlatformDependent.putLong(memoryAddress, expectedLongValue);
                assertThat(PlatformDependent.getLong(memoryAddress)).isEqualTo(expectedLongValue);

                int expectedIntValue = 0x11223344;
                PlatformDependent.putInt(memoryAddress, expectedIntValue);
                assertThat(PlatformDependent.getInt(memoryAddress)).isEqualTo(expectedIntValue);

                byte expectedByteValue = 0x55;
                PlatformDependent.putByte(memoryAddress, expectedByteValue);
                assertThat(PlatformDependent.getByte(memoryAddress)).isEqualTo(expectedByteValue);
            } finally {
                PlatformDependent.freeMemory(memoryAddress);
            }
        } else {
            assertThat(PlatformDependent.directBufferPreferred()).isFalse();
        }
    }
}
