/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongodb_driver_core;

import com.mongodb.internal.connection.tlschannel.util.DirectBufferDeallocator;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThatCode;

public class DirectBufferDeallocatorInnerJava9DeallocatorTest {
    @Test
    void deallocateReleasesDirectByteBufferWithJava9Deallocator() {
        final ByteBuffer directBuffer = ByteBuffer.allocateDirect(Long.BYTES);
        directBuffer.putLong(42L);
        directBuffer.flip();

        final DirectBufferDeallocator deallocator = new DirectBufferDeallocator();

        assertThatCode(() -> deallocator.deallocate(directBuffer)).doesNotThrowAnyException();
    }
}
