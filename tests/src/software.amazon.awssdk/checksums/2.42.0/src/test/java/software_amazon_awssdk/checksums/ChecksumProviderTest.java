/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.checksums;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.CRC32C;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import software.amazon.awssdk.checksums.DefaultChecksumAlgorithm;
import software.amazon.awssdk.checksums.SdkChecksum;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ChecksumProviderTest {
    private static final byte[] PAYLOAD =
        "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EXTRA_PAYLOAD = " on native image".getBytes(StandardCharsets.UTF_8);
    private static final String JAVA_CRC32C_PROVIDER_TYPE = "CrcCombineOnMarkChecksum";

    @Test
    @Order(1)
    void crc32cImplementationUsesJavaCrc32cConstructor() {
        SdkChecksum checksum = SdkChecksum.forAlgorithm(DefaultChecksumAlgorithm.CRC32C);
        CRC32C expected = new CRC32C();

        checksum.update(PAYLOAD);
        expected.update(PAYLOAD, 0, PAYLOAD.length);

        assertThat(checksum.getClass().getSimpleName()).isEqualTo(JAVA_CRC32C_PROVIDER_TYPE);
        assertThat(checksum.getValue()).isEqualTo(expected.getValue());
        assertThat(checksum.getChecksumBytes()).hasSize(Integer.BYTES);
    }

    @Test
    @Order(2)
    void crc64NvmeAlgorithmUsesCrtConstructor() {
        SdkChecksum checksum = SdkChecksum.forAlgorithm(DefaultChecksumAlgorithm.CRC64NVME);

        checksum.update(PAYLOAD);
        long markedValue = checksum.getValue();

        checksum.mark(Integer.MAX_VALUE);
        checksum.update(EXTRA_PAYLOAD);

        assertThat(checksum.getValue()).isNotEqualTo(markedValue);

        checksum.reset();

        assertThat(checksum.getValue()).isEqualTo(markedValue);
        assertThat(checksum.getChecksumBytes()).hasSize(Long.BYTES);
    }

    @Test
    @Order(3)
    void crc32AlgorithmSupportsMarkAndReset() {
        SdkChecksum checksum = SdkChecksum.forAlgorithm(DefaultChecksumAlgorithm.CRC32);
        CRC32 expected = new CRC32();

        checksum.update(PAYLOAD);
        expected.update(PAYLOAD, 0, PAYLOAD.length);
        long markedValue = checksum.getValue();

        checksum.mark(Integer.MAX_VALUE);
        checksum.update(EXTRA_PAYLOAD);

        assertThat(checksum.getValue()).isNotEqualTo(markedValue);

        checksum.reset();

        assertThat(checksum.getClass().getSimpleName()).isEqualTo("Crc32Checksum");
        assertThat(checksum.getValue()).isEqualTo(expected.getValue());
        assertThat(checksum.getChecksumBytes()).hasSize(Integer.BYTES);
    }
}
