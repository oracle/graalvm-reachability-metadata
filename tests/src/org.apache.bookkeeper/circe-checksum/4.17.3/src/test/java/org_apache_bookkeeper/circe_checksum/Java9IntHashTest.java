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
    private static final byte[] PAYLOAD = "Apache BookKeeper Circe checksum Java9IntHash coverage"
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void computesChecksumForByteArraySliceWithJava9Crc32c() {
        Java9IntHash hash = newJava9IntHash();

        int offset = 3;
        int length = PAYLOAD.length - 7;
        int checksum = hash.resume(0, PAYLOAD, offset, length);

        assertThat(Integer.toUnsignedLong(checksum)).isEqualTo(expectedCrc32c(offset, length));
    }

    @Test
    void computesChecksumForDirectMemoryAddressBufferWithJava9Crc32c() {
        Java9IntHash hash = newJava9IntHash();
        ByteBuf buffer = Unpooled.directBuffer(PAYLOAD.length);
        try {
            buffer.writeBytes(PAYLOAD);
            assertThat(buffer.hasMemoryAddress()).isTrue();

            int offset = 5;
            int length = PAYLOAD.length - 11;
            int checksum = hash.calculate(buffer, offset, length);

            assertThat(Integer.toUnsignedLong(checksum)).isEqualTo(expectedCrc32c(offset, length));
        } finally {
            buffer.release();
        }
    }

    private static Java9IntHash newJava9IntHash() {
        return new Java9IntHash();
    }

    private static long expectedCrc32c(int offset, int length) {
        CRC32C crc32c = new CRC32C();
        crc32c.update(PAYLOAD, offset, length);
        return crc32c.getValue();
    }
}
