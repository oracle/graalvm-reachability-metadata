/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.ByteBuffer;

import org.apache.kafka.common.utils.ByteBufferUnmapper;
import org.junit.jupiter.api.Test;

public class ByteBufferUnmapperTest {

    @Test
    void unmapUsesUnsafeCleanerForDirectBuffers() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(64);
        buffer.putInt(0, 42);

        assertThatNoException().isThrownBy(() -> ByteBufferUnmapper.unmap("direct-buffer", buffer));
    }
}
