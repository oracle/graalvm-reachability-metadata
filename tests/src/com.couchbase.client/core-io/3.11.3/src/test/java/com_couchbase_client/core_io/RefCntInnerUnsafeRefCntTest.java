/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.netty.buffer.ByteBuf;
import com.couchbase.client.core.deps.io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RefCntInnerUnsafeRefCntTest {
    @Test
    void exercisesPublicApi() throws Exception {
        ByteBuf buffer = Unpooled.directBuffer(32);
        try {
            buffer.writeLong(0x1020304050607080L);
            buffer.writeInt(42);
            assertEquals(0x1020304050607080L, buffer.readLong());
            assertEquals(42, buffer.readInt());
            buffer.retain();
            assertEquals(2, buffer.refCnt());
            assertFalse(buffer.release());
            assertEquals(1, buffer.refCnt());
        } finally {
            assertTrue(buffer.release());
        }
        assertEquals(0, buffer.refCnt());
    }
}
