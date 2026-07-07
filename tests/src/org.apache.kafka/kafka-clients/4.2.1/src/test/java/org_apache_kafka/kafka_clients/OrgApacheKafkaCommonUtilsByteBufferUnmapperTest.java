/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.utils.ByteBufferUnmapper;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OrgApacheKafkaCommonUtilsByteBufferUnmapperTest {

    @Test
    void unmapsDirectBufferAllocatedByKafkaCaller() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(32);
        buffer.putInt(42);
        buffer.flip();

        assertThatCode(() -> ByteBufferUnmapper.unmap("test-direct-buffer", buffer))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsHeapBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(32);

        assertThatThrownBy(() -> ByteBufferUnmapper.unmap("test-heap-buffer", buffer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unmapping only works with direct buffers");
    }
}
