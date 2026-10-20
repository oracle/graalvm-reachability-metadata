/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import com.scurrilous.circe.checksum.Java9IntHash;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32C;
import org.apache.pulsar.shade.io.netty.buffer.ByteBuf;
import org.apache.pulsar.shade.io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

public class Java9IntHashTest {
    private static final byte[] PAYLOAD = "pulsar-java9-crc32c".getBytes(StandardCharsets.UTF_8);

    @Test
    void computesCrc32cFromArrayAndDirectBuffer() {
        Java9IntHash hash = new Java9IntHash();
        int arrayHash = hash.resume(0, PAYLOAD, 0, PAYLOAD.length);
        ByteBuf buffer = Unpooled.directBuffer(PAYLOAD.length);
        try {
            buffer.writeBytes(PAYLOAD);
            int directHash = hash.resume(0, buffer, 0, PAYLOAD.length);

            assertThat(Integer.toUnsignedLong(arrayHash)).isEqualTo(expectedChecksum());
            assertThat(Integer.toUnsignedLong(directHash)).isEqualTo(expectedChecksum());
        } finally {
            buffer.release();
        }
    }

    private static long expectedChecksum() {
        CRC32C checksum = new CRC32C();
        checksum.update(PAYLOAD, 0, PAYLOAD.length);
        return checksum.getValue();
    }
}
