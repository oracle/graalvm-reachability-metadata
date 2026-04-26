/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import java.nio.ByteBuffer;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CleanerJava9Test {
    @Test
    void freesDirectBuffersWithTheJava9InvokeCleanerPath() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(64);
        buffer.putInt(0, 42);

        Assertions.assertTrue(buffer.isDirect(), "Expected a direct buffer");
        Assertions.assertTrue(PlatformDependent.javaVersion() >= 9, "Expected the Java 9+ cleaner path");
        Assertions.assertTrue(PlatformDependent.hasUnsafe(), "Expected Unsafe-backed direct buffer cleanup");
        Assertions.assertDoesNotThrow(() -> PlatformDependent.freeDirectBuffer(buffer));
    }
}
