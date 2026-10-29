/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.buffer.ByteBuf;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

public class ReferenceCountUpdaterTest {
    @Test
    void retainsAndReleasesTransportBuffers() {
        ByteBuf buffer = Unpooled.buffer(8);
        try {
            buffer.writeInt(42);
            buffer.retain();
            assertEquals(2, buffer.refCnt());
            assertFalse(buffer.release());
            assertEquals(42, buffer.readInt());
        } finally {
            assertTrue(buffer.release());
        }
        assertEquals(0, buffer.refCnt());
    }
}
