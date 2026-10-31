/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
public class CompositeByteBufTest {
    @Test
    void readsAcrossComponentsAndPreservesContentWhenConsolidated() {
        CompositeByteBuf composite = Unpooled.compositeBuffer();
        ByteBuf first = Unpooled.wrappedBuffer(new byte[]{1, 2});
        ByteBuf second = Unpooled.wrappedBuffer(new byte[]{3, 4, 5, 6});
        try {
            composite.addComponents(true, first, second);
            assertEquals(2, composite.numComponents());
            assertEquals(6, composite.readableBytes());
            assertEquals(0x01020304, composite.getInt(0));
            composite.setShort(1, 0x1122);
            assertEquals(0x11, first.getUnsignedByte(1));
            assertEquals(0x22, second.getUnsignedByte(0));

            composite.consolidate();
            assertEquals(1, composite.numComponents());
            assertEquals(0x01112204, composite.readInt());
            assertEquals(0x0506, composite.readUnsignedShort());
            assertEquals(0, composite.readableBytes());
        } finally {
            composite.release();
        }
        assertEquals(0, first.refCnt());
        assertEquals(0, second.refCnt());
    }
}
