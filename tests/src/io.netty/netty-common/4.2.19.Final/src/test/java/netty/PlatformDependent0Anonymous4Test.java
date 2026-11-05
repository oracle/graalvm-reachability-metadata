/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.nio.ByteBuffer;

import io.netty.util.internal.CleanableDirectBuffer;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PlatformDependent0Anonymous4Test {
    @Test
    void allocatesManagedDirectStorage() {
        CleanableDirectBuffer allocation = PlatformDependent.allocateDirect(64, true);
        try {
            ByteBuffer buffer = allocation.buffer();
            Assertions.assertTrue(buffer.isDirect());
            Assertions.assertEquals(64, buffer.capacity());
            buffer.putInt(0, 123456);
            buffer.putInt(60, -654321);
            Assertions.assertEquals(123456, buffer.getInt(0));
            Assertions.assertEquals(-654321, buffer.getInt(60));
        } finally {
            allocation.clean();
        }
    }
}
