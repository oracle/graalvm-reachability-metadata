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

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonUtilsByteBufferUnmapperTest {

    @Test
    void unmapsDirectBufferThroughPublicUtilityApi() throws Exception {
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(Integer.BYTES);
        directBuffer.putInt(42);
        directBuffer.flip();

        assertThat(directBuffer.isDirect()).isTrue();

        ByteBufferUnmapper.unmap("native-image-test-direct-buffer", directBuffer);
    }
}
