/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.sdk_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.checksums.Crc32CChecksum;
import software.amazon.awssdk.crt.checksums.CRC32C;

@SuppressWarnings("deprecation")
public class Crc32CChecksumTest {
    private static final byte[] PREFIX = "The quick brown ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DISCARDED_SUFFIX = "fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
    private static final byte[] REPLACEMENT_SUFFIX = "cat naps".getBytes(StandardCharsets.UTF_8);

    @Test
    void markAndResetCloneTheCrtChecksumState() {
        Crc32CChecksum checksum = new Crc32CChecksum();
        checksum.update(PREFIX, 0, PREFIX.length);
        long markedValue = checksum.getValue();

        checksum.mark(Integer.MAX_VALUE);
        checksum.update(DISCARDED_SUFFIX, 0, DISCARDED_SUFFIX.length);
        assertThat(checksum.getValue()).isNotEqualTo(markedValue);

        checksum.reset();
        assertThat(checksum.getValue()).isEqualTo(markedValue);

        checksum.update(REPLACEMENT_SUFFIX, 0, REPLACEMENT_SUFFIX.length);

        CRC32C expected = new CRC32C();
        expected.update(PREFIX, 0, PREFIX.length);
        expected.update(REPLACEMENT_SUFFIX, 0, REPLACEMENT_SUFFIX.length);
        assertThat(checksum.getValue()).isEqualTo(expected.getValue());
        assertThat(checksum.getChecksumBytes()).hasSize(Integer.BYTES);
    }
}
