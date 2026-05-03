/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.checksums_spi;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.checksums.spi.ChecksumAlgorithm;

import static org.assertj.core.api.Assertions.assertThat;

public class Checksums_spiTest {
    @Test
    void lambdaImplementationReturnsTheAlgorithmIdentifier() {
        ChecksumAlgorithm algorithm = () -> "CRC32";

        assertThat(algorithm.algorithmId()).isEqualTo("CRC32");
    }

    @Test
    void concreteImplementationCanExposeStandardAwsChecksumNames() {
        List<ChecksumAlgorithm> algorithms = List.of(
                new FixedChecksumAlgorithm("CRC32"),
                new FixedChecksumAlgorithm("CRC32C"),
                new FixedChecksumAlgorithm("SHA1"),
                new FixedChecksumAlgorithm("SHA256"));

        assertThat(algorithmIds(algorithms)).containsExactly("CRC32", "CRC32C", "SHA1", "SHA256");
    }

    @Test
    void enumImplementationCanBeConsumedThroughTheSpiInterface() {
        List<ChecksumAlgorithm> algorithms = List.of(TestChecksumAlgorithm.values());

        Map<String, ChecksumAlgorithm> byLowerCaseId = algorithms.stream()
                .collect(Collectors.toMap(algorithm -> algorithm.algorithmId().toLowerCase(Locale.ROOT), Function.identity()));

        assertThat(byLowerCaseId)
                .containsEntry("crc32", TestChecksumAlgorithm.CRC32)
                .containsEntry("crc32c", TestChecksumAlgorithm.CRC32C)
                .containsEntry("sha1", TestChecksumAlgorithm.SHA1)
                .containsEntry("sha256", TestChecksumAlgorithm.SHA256);
    }

    @Test
    void callersCanUseTheInterfacePolymorphically() {
        ChecksumAlgorithm lambdaAlgorithm = () -> "CRC64NVME";
        ChecksumAlgorithm objectAlgorithm = new FixedChecksumAlgorithm("SHA256");
        ChecksumAlgorithm enumAlgorithm = TestChecksumAlgorithm.CRC32C;

        assertThat(algorithmIds(List.of(lambdaAlgorithm, objectAlgorithm, enumAlgorithm)))
                .containsExactly("CRC64NVME", "SHA256", "CRC32C");
    }

    private static List<String> algorithmIds(List<ChecksumAlgorithm> algorithms) {
        return algorithms.stream()
                .map(ChecksumAlgorithm::algorithmId)
                .collect(Collectors.toList());
    }

    private static final class FixedChecksumAlgorithm implements ChecksumAlgorithm {
        private final String algorithmId;

        private FixedChecksumAlgorithm(String algorithmId) {
            this.algorithmId = algorithmId;
        }

        @Override
        public String algorithmId() {
            return algorithmId;
        }
    }

    private enum TestChecksumAlgorithm implements ChecksumAlgorithm {
        CRC32("CRC32"),
        CRC32C("CRC32C"),
        SHA1("SHA1"),
        SHA256("SHA256");

        private final String algorithmId;

        TestChecksumAlgorithm(String algorithmId) {
            this.algorithmId = algorithmId;
        }

        @Override
        public String algorithmId() {
            return algorithmId;
        }
    }
}
