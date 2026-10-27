/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bookkeeper.circe_checksum;

import static org.assertj.core.api.Assertions.assertThat;

import com.scurrilous.circe.checksum.Java9IntHash;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32C;
import org.junit.jupiter.api.Test;

public class Java9IntHashTest {
    private static final byte[] PAYLOAD = "bookkeeper-circe-checksum-java9-crc32c".getBytes(StandardCharsets.UTF_8);

    @Test
    void resumeWithByteArrayUsesJavaCrc32cUpdateBytes() {
        Java9IntHash hash = new Java9IntHash();
        int offset = 3;
        int length = PAYLOAD.length - 7;

        int actual = hash.resume(0, PAYLOAD, offset, length);

        assertThat(Integer.toUnsignedLong(actual)).isEqualTo(expectedCrc32c(PAYLOAD, offset, length));
    }

    @Test
    void resumeWithMemoryAddressByteBufUsesJavaCrc32cUpdateDirectByteBuffer() {
        Java9IntHash hash = new Java9IntHash();
        ByteBuf buffer = Unpooled.directBuffer(PAYLOAD.length);
        try {
            buffer.writeBytes(PAYLOAD);
            assertThat(buffer.hasMemoryAddress()).isTrue();

            int offset = 5;
            int length = PAYLOAD.length - 11;
            int actual = hash.resume(0, buffer, offset, length);

            assertThat(Integer.toUnsignedLong(actual)).isEqualTo(expectedCrc32c(PAYLOAD, offset, length));
        } finally {
            buffer.release();
        }
    }

    private static long expectedCrc32c(byte[] payload, int offset, int length) {
        CRC32C checksum = new CRC32C();
        checksum.update(payload, offset, length);
        return checksum.getValue();
    }
}
