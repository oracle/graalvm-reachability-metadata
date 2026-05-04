/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.checksums;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import software.amazon.awssdk.checksums.DefaultChecksumAlgorithm;
import software.amazon.awssdk.checksums.SdkChecksum;
import org.junit.jupiter.api.Test;

public class CrcChecksumProviderTest {
    private static final byte[] CHECKSUM_INPUT = "123456789".getBytes(StandardCharsets.US_ASCII);

    @Test
    void crc32cAlgorithmInstantiatesJdkCrc32cConstructor() {
        SdkChecksum checksum = SdkChecksum.forAlgorithm(DefaultChecksumAlgorithm.CRC32C);

        checksum.update(CHECKSUM_INPUT);

        assertThat(checksum.getValue()).isEqualTo(0xE3069283L);
        assertThat(checksum.getChecksumBytes())
            .containsExactly((byte) 0xE3, (byte) 0x06, (byte) 0x92, (byte) 0x83);
    }

    @Test
    void crc64NvmeAlgorithmInstantiatesAvailableCrtConstructor() {
        SdkChecksum checksum = SdkChecksum.forAlgorithm(DefaultChecksumAlgorithm.CRC64NVME);

        checksum.update(CHECKSUM_INPUT);

        assertThat(checksum.getValue()).isEqualTo(477L);
        assertThat(checksum.getChecksumBytes())
            .containsExactly((byte) 0, (byte) 0, (byte) 0, (byte) 0,
                             (byte) 0, (byte) 0, (byte) 1, (byte) 0xDD);
    }
}
