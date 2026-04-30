/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.kafka.common.utils.ByteBufferUnmapper;
import org.junit.jupiter.api.Test;

public class ByteBufferUnmapperTest {
    @Test
    void initializesUnsafeUnmapperForDirectByteBuffer() throws IOException {
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(16);
        directBuffer.putInt(42);
        directBuffer.flip();

        try {
            ByteBufferUnmapper.unmap("direct test buffer", directBuffer);
        } catch (UnsupportedOperationException exception) {
            assertTrue(exception.getMessage().contains("Unmapping is not supported"));
        }
    }
}
