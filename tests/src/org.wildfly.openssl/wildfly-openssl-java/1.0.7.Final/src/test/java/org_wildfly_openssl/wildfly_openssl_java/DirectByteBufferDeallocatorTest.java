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

import static org.assertj.core.api.Assertions.assertThat;

public class DirectByteBufferDeallocatorTest {
    @Test
    void freeReleasesDirectByteBufferThroughPublicApi() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(32);
        buffer.putInt(0, 42);

        DirectByteBufferDeallocator.free(buffer);

        assertThat(buffer.isDirect()).isTrue();
    }

    @Test
    void freeIgnoresNullAndHeapByteBuffers() {
        ByteBuffer heapBuffer = ByteBuffer.allocate(16);

        DirectByteBufferDeallocator.free(null);
        DirectByteBufferDeallocator.free(heapBuffer);

        assertThat(heapBuffer.isDirect()).isFalse();
    }
}
