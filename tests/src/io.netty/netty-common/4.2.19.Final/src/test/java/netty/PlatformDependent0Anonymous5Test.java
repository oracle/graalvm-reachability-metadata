/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PlatformDependent0Anonymous5Test {
    @Test
    void readsAndWritesUnalignedDirectBufferValues() {
        ByteBuf buffer = Unpooled.directBuffer(32);
        try {
            buffer.setLong(1, 0x1020304050607080L);
            buffer.setIntLE(11, 0x12345678);
            Assertions.assertTrue(buffer.isDirect());
            Assertions.assertEquals(0x1020304050607080L, buffer.getLong(1));
            Assertions.assertEquals(0x12345678, buffer.getIntLE(11));
            Assertions.assertEquals(0x10, buffer.getUnsignedByte(1));
            Assertions.assertEquals(0x78, buffer.getUnsignedByte(11));
        } finally {
            buffer.release();
        }
    }
}
