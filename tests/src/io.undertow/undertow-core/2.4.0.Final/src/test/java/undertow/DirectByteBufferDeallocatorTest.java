/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import io.undertow.server.DirectByteBufferDeallocator;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class DirectByteBufferDeallocatorTest {

    @Test
    void freeIgnoresNullAndHeapByteBuffers() {
        ByteBuffer heapBuffer = ByteBuffer.allocate(16);

        DirectByteBufferDeallocator.free(null);
        DirectByteBufferDeallocator.free(heapBuffer);

        assertThat(heapBuffer.isDirect()).isFalse();
    }

    @Test
    void freeCleansExpiredDirectByteBufferWhenAnotherBufferIsFreed() throws InterruptedException {
        ByteBuffer firstBuffer = ByteBuffer.allocateDirect(32);
        firstBuffer.putInt(0, 42);

        DirectByteBufferDeallocator.free(firstBuffer);
        Thread.sleep(150L);

        ByteBuffer secondBuffer = ByteBuffer.allocateDirect(32);
        DirectByteBufferDeallocator.free(secondBuffer);

        assertThat(firstBuffer.isDirect()).isTrue();
        assertThat(secondBuffer.isDirect()).isTrue();
    }
}
