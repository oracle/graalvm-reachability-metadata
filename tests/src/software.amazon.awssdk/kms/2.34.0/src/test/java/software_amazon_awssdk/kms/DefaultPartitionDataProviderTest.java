/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.kms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.kms.endpoints.internal.DefaultPartitionDataProvider;
import software.amazon.awssdk.services.kms.endpoints.internal.Partition;
import software.amazon.awssdk.services.kms.endpoints.internal.Partitions;

public class DefaultPartitionDataProviderTest {
    @Test
    void loadPartitionsReadsAwsPartitionMetadataFromClasspathResource() {
        System.clearProperty("aws.partitionsFile");

        Partitions partitions = new DefaultPartitionDataProvider().loadPartitions();

        assertThat(partitions.version()).isNotBlank();
        assertThat(partitions.partitions()).extracting(Partition::id).contains("aws", "aws-cn", "aws-us-gov");

        Partition awsPartition = partitionById(partitions, "aws");
        assertThat(awsPartition.regionMatches("us-east-1")).isTrue();
        assertThat(awsPartition.regions()).containsKeys("us-east-1", "us-west-2");
        assertThat(awsPartition.outputs().dnsSuffix()).isEqualTo("amazonaws.com");
        assertThat(awsPartition.outputs().supportsDualStack()).isTrue();
        assertThat(awsPartition.outputs().supportsFips()).isTrue();
    }

    private static Partition partitionById(Partitions partitions, String partitionId) {
        return partitions.partitions().stream()
                .filter(partition -> partitionId.equals(partition.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing partition: " + partitionId));
    }
}
