/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.sns;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.services.sns.endpoints.internal.DefaultPartitionDataProvider;
import software.amazon.awssdk.services.sns.endpoints.internal.Partition;
import software.amazon.awssdk.services.sns.endpoints.internal.Partitions;

public class DefaultPartitionDataProviderTest {
    @Test
    void loadPartitionsReadsPartitionMetadata() {
        String propertyName = SdkSystemSetting.AWS_PARTITIONS_FILE.property();
        String originalPropertyValue = System.getProperty(propertyName);
        System.clearProperty(propertyName);

        try {
            Partitions partitions = new DefaultPartitionDataProvider().loadPartitions();

            assertThat(partitions.version()).isEqualTo("1.1");
            assertThat(partitions.partitions()).extracting(Partition::id).contains("aws", "aws-cn", "aws-us-gov");
            assertThat(partitions.partitions())
                    .filteredOn(partition -> "aws".equals(partition.id()))
                    .singleElement()
                    .satisfies(partition -> {
                        assertThat(partition.regionMatches("us-east-1")).isTrue();
                        assertThat(partition.outputs().dnsSuffix()).isEqualTo("amazonaws.com");
                        assertThat(partition.outputs().supportsDualStack()).isTrue();
                    });
        } finally {
            if (originalPropertyValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, originalPropertyValue);
            }
        }
    }
}
