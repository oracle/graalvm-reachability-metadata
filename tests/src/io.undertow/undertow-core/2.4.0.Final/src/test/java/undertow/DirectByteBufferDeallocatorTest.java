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
import static org.assertj.core.api.Assertions.assertThatCode;

public class DirectByteBufferDeallocatorTest {

    @Test
    void directByteBuffersAreAcceptedForDelayedDeallocation() throws InterruptedException {
        ByteBuffer firstBuffer = ByteBuffer.allocateDirect(8);
        ByteBuffer secondBuffer = ByteBuffer.allocateDirect(8);

        assertThat(firstBuffer.isDirect()).isTrue();
        assertThat(secondBuffer.isDirect()).isTrue();

        DirectByteBufferDeallocator.free(firstBuffer);
        Thread.sleep(150L);

        assertThatCode(() -> DirectByteBufferDeallocator.free(secondBuffer)).doesNotThrowAnyException();
    }
}
