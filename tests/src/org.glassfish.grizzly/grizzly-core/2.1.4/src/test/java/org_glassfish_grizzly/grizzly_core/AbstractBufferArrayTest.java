/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_core;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.BufferArray;
import org.glassfish.grizzly.memory.ByteBufferArray;
import org.glassfish.grizzly.memory.MemoryManager;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractBufferArrayTest {
    @Test
    void growsAByteBufferArrayAndRestoresEachBuffersPositionAndLimit() {
        ByteBufferArray buffers = ByteBufferArray.create();
        try {
            for (int i = 0; i < 5; i++) {
                ByteBuffer buffer = ByteBuffer.allocate(8);
                buffer.put(1, (byte) (10 + i));
                buffer.position(1).limit(6);
                buffers.add(buffer);
                buffer.position(3).limit(4);
            }

            assertThat(buffers.size()).isEqualTo(5);
            buffers.restore();
            for (int i = 0; i < buffers.size(); i++) {
                ByteBuffer buffer = buffers.getArray()[i];
                assertThat(buffers.getInitialPosition(i)).isEqualTo(1);
                assertThat(buffers.getInitialLimit(i)).isEqualTo(6);
                assertThat(buffers.getInitialBufferSize(i)).isEqualTo(5);
                assertThat(buffer.position()).isEqualTo(1);
                assertThat(buffer.limit()).isEqualTo(6);
                assertThat(buffer.get()).isEqualTo((byte) (10 + i));
            }
        } finally {
            buffers.recycle();
        }
    }

    @Test
    void restoresAGrizzlyBufferToExplicitBoundsAndRecyclesTheArray() {
        Buffer buffer = MemoryManager.DEFAULT_MEMORY_MANAGER.allocate(8);
        BufferArray buffers = BufferArray.create();
        try {
            buffer.put(2, (byte) 42);
            buffer.position(1).limit(6);
            buffers.add(buffer, 2, 7);
            buffer.position(4).limit(5);

            assertThat(buffers.getInitialPosition(0)).isEqualTo(1);
            assertThat(buffers.getInitialLimit(0)).isEqualTo(6);
            assertThat(buffers.getArray()[0]).isSameAs(buffer);
            buffers.restore();
            assertThat(buffer.position()).isEqualTo(2);
            assertThat(buffer.limit()).isEqualTo(7);
            assertThat(buffer.get()).isEqualTo((byte) 42);
        } finally {
            buffers.recycle();
            buffer.dispose();
        }
        assertThat(buffers.size()).isZero();
        assertThat(buffers.getArray()[0]).isNull();
    }
}
