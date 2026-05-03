/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.checksums_spi;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

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

    @Test
    void implementationCanPairTheSpiIdentifierWithChecksumComputation() {
        StatefulChecksumAlgorithm algorithm = new StatefulChecksumAlgorithm("CRC32", new CRC32());

        algorithm.update("hello ".getBytes(StandardCharsets.UTF_8));
        algorithm.update("world".getBytes(StandardCharsets.UTF_8));

        assertThat(algorithm.algorithmId()).isEqualTo("CRC32");
        assertThat(algorithm.getValue()).isEqualTo(0x0D4A1185L);
    }

    @Test
    void callersCanResolveARequestedAlgorithmFromRegisteredImplementations() {
        ChecksumAlgorithm crc32 = new FixedChecksumAlgorithm("CRC32");
        ChecksumAlgorithm crc32c = new FixedChecksumAlgorithm("CRC32C");
        ChecksumAlgorithm sha256 = new FixedChecksumAlgorithm("SHA256");
        List<ChecksumAlgorithm> registeredAlgorithms = List.of(crc32, crc32c, sha256);

        assertThat(resolveAlgorithm(registeredAlgorithms, "CRC32C")).contains(crc32c);
        assertThat(resolveAlgorithm(registeredAlgorithms, "crc32c")).isEmpty();
        assertThat(resolveAlgorithm(registeredAlgorithms, "SHA1")).isEmpty();
    }

    private static Optional<ChecksumAlgorithm> resolveAlgorithm(List<ChecksumAlgorithm> algorithms, String requestedAlgorithmId) {
        return algorithms.stream()
                .filter(algorithm -> algorithm.algorithmId().equals(requestedAlgorithmId))
                .findFirst();
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

    private static final class StatefulChecksumAlgorithm implements ChecksumAlgorithm {
        private final String algorithmId;
        private final Checksum checksum;

        private StatefulChecksumAlgorithm(String algorithmId, Checksum checksum) {
            this.algorithmId = algorithmId;
            this.checksum = checksum;
        }

        @Override
        public String algorithmId() {
            return algorithmId;
        }

        private void update(byte[] bytes) {
            checksum.update(bytes, 0, bytes.length);
        }

        private long getValue() {
            return checksum.getValue();
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
