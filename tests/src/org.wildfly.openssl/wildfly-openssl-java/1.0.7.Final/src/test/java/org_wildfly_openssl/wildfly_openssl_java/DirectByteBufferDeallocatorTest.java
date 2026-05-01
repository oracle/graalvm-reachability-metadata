/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_openssl.wildfly_openssl_java;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.wildfly.openssl.util.DirectByteBufferDeallocator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class DirectByteBufferDeallocatorTest {

    @Test
    void freeAcceptsDirectByteBuffer() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(64);
        buffer.putInt(0x12345678);

        assertDoesNotThrow(() -> DirectByteBufferDeallocator.free(buffer));
    }

    @Test
    void freeIgnoresBuffersWithoutDirectMemory() {
        ByteBuffer heapBuffer = ByteBuffer.allocate(64);
        heapBuffer.putInt(0x12345678);

        assertDoesNotThrow(() -> DirectByteBufferDeallocator.free(heapBuffer));
        assertDoesNotThrow(() -> DirectByteBufferDeallocator.free(null));
    }
}
