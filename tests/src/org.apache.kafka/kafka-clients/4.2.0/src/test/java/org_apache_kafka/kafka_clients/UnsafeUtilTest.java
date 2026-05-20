/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.ByteString;
import org.apache.kafka.shaded.io.opentelemetry.proto.profiles.v1experimental.ProfilesData;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsafeUtilTest {

    @Test
    void parsesShadedTelemetryMessageFromDirectByteBuffer() throws Exception {
        byte[] bytes = GeneratedMessageV3Test.sampleProfilesData().toByteArray();
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(bytes.length);
        directBuffer.put(bytes);
        directBuffer.flip();

        ByteBuffer copySource = directBuffer.asReadOnlyBuffer();
        ProfilesData parsed = ProfilesData.parseFrom(directBuffer);
        ByteString copied = ByteString.copyFrom(copySource);

        assertThat(parsed.getSerializedSize()).isEqualTo(bytes.length);
        assertThat(copied.size()).isEqualTo(bytes.length);
    }
}
