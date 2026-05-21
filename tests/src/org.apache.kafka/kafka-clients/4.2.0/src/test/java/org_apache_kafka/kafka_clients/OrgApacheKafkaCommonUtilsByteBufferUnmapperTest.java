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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OrgApacheKafkaCommonUtilsByteBufferUnmapperTest {

    @Test
    void rejectsHeapBuffersThroughPublicUnmapApi() {
        ByteBuffer heapBuffer = ByteBuffer.allocate(16);

        assertThatThrownBy(() -> ByteBufferUnmapper.unmap("heap-buffer", heapBuffer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("direct buffers");
    }
}
