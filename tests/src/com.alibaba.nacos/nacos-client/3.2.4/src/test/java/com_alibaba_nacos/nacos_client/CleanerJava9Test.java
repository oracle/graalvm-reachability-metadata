/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.internal.PlatformDependent;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

public class CleanerJava9Test {
    @Test
    void releasesDirectBuffersAfterUse() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(32);
        try {
            buffer.putInt(0, 42);
            assertEquals(42, buffer.getInt(0));
        } finally {
            PlatformDependent.freeDirectBuffer(buffer);
        }
    }
}
