/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.util.internal.PlatformDependent;

public class CleanerJava9Test {
    @Test
    void releasesOwnedDirectBuffersAfterTheirLastUse() {
        for (int i = 0; i < 16; i++) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
            try {
                buffer.putLong(0, i);
                assertEquals(i, buffer.getLong(0));
            } finally {
                PlatformDependent.freeDirectBuffer(buffer);
            }
        }
    }
}
