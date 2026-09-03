/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.nio.ByteBuffer;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PlatformDependent0Test {
    @Test
    public void initializesDirectBufferPlatformSupport() {
        boolean hasUnsafe = PlatformDependent.hasUnsafe();
        assertThat(PlatformDependent.maxDirectMemory()).isGreaterThanOrEqualTo(0L);

        ByteBuffer directBuffer = ByteBuffer.allocateDirect(Byte.BYTES);
        directBuffer.put(0, (byte) 0x5A);
        try {
            if (hasUnsafe) {
                long address = PlatformDependent.directBufferAddress(directBuffer);
                assertThat(address).isPositive();
                assertThat(PlatformDependent.getByte(address)).isEqualTo((byte) 0x5A);
            } else {
                assertThat(PlatformDependent.directBufferPreferred()).isFalse();
            }
        } finally {
            PlatformDependent.freeDirectBuffer(directBuffer);
        }
    }
}
