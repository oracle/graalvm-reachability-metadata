/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.ssm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.services.ssm.endpoints.internal.DefaultPartitionDataProvider;
import software.amazon.awssdk.services.ssm.endpoints.internal.Partition;
import software.amazon.awssdk.services.ssm.endpoints.internal.Partitions;

public class DefaultPartitionDataProviderTest {
    @Test
    void loadPartitionsReadsPartitionMetadataFromClasspath() {
        String propertyName = SdkSystemSetting.AWS_PARTITIONS_FILE.property();
        String previousPartitionsFile = System.getProperty(propertyName);
        System.clearProperty(propertyName);

        try {
            Partitions partitions = new DefaultPartitionDataProvider().loadPartitions();

            assertThat(partitions.version()).isNotBlank();
            assertThat(partitions.partitions()).extracting(Partition::id).contains("aws", "aws-cn", "aws-us-gov");

            Partition awsPartition = partitionWithId(partitions, "aws");
            assertThat(awsPartition.regions()).containsKey("us-east-1");
            assertThat(awsPartition.regionMatches("us-west-2")).isTrue();
            assertThat(awsPartition.outputs().dnsSuffix()).isEqualTo("amazonaws.com");
            assertThat(awsPartition.outputs().implicitGlobalRegion()).isEqualTo("us-east-1");
        } finally {
            if (previousPartitionsFile == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previousPartitionsFile);
            }
        }
    }

    private static Partition partitionWithId(Partitions partitions, String id) {
        return partitions.partitions().stream()
                .filter(partition -> id.equals(partition.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Partition not found: " + id));
    }
}
