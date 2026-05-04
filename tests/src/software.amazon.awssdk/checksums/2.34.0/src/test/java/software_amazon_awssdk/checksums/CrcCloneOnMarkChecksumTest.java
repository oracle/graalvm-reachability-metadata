/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.checksums;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import software.amazon.awssdk.checksums.internal.CrcCloneOnMarkChecksum;
import software.amazon.awssdk.checksums.internal.SdkCrc32Checksum;
import org.junit.jupiter.api.Test;

public class CrcCloneOnMarkChecksumTest {
    @Test
    void markAndResetCloneTheUnderlyingCloneableChecksum() {
        CrcCloneOnMarkChecksum checksum = new CrcCloneOnMarkChecksum(SdkCrc32Checksum.create());
        byte[] prefix = "123".getBytes(StandardCharsets.US_ASCII);
        byte[] suffix = "456789".getBytes(StandardCharsets.US_ASCII);
        byte[] divergentSuffix = "abcdef".getBytes(StandardCharsets.US_ASCII);

        checksum.update(prefix);
        long valueAtMark = checksum.getValue();
        checksum.mark(1024);

        checksum.update(divergentSuffix);
        assertThat(checksum.getValue()).isNotEqualTo(valueAtMark);

        checksum.reset();
        assertThat(checksum.getValue()).isEqualTo(valueAtMark);

        checksum.update(suffix);
        assertThat(checksum.getValue()).isEqualTo(0xCBF43926L);
        assertThat(checksum.getChecksumBytes())
            .containsExactly((byte) 0xCB, (byte) 0xF4, (byte) 0x39, (byte) 0x26);
    }
}
