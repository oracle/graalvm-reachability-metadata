/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.checksums;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.checksums.internal.CrcCloneOnMarkChecksum;
import software.amazon.awssdk.checksums.internal.SdkCrc32CChecksum;

public class CrcCloneOnMarkChecksumTest {
    private static final byte[] PREFIX = "The quick brown ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DISCARDED_SUFFIX = "fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
    private static final byte[] REPLACEMENT_SUFFIX = "cat naps".getBytes(StandardCharsets.UTF_8);

    @Test
    void markAndResetCloneTheChecksumState() {
        CrcCloneOnMarkChecksum checksum = new CrcCloneOnMarkChecksum(SdkCrc32CChecksum.create());
        checksum.update(PREFIX);
        long markedValue = checksum.getValue();

        checksum.mark(Integer.MAX_VALUE);
        checksum.update(DISCARDED_SUFFIX);
        assertThat(checksum.getValue()).isNotEqualTo(markedValue);

        checksum.reset();
        assertThat(checksum.getValue()).isEqualTo(markedValue);

        checksum.update(REPLACEMENT_SUFFIX);

        SdkCrc32CChecksum expected = SdkCrc32CChecksum.create();
        expected.update(PREFIX);
        expected.update(REPLACEMENT_SUFFIX);
        assertThat(checksum.getValue()).isEqualTo(expected.getValue());
        assertThat(checksum.getChecksumBytes()).hasSize(Integer.BYTES);
    }
}
