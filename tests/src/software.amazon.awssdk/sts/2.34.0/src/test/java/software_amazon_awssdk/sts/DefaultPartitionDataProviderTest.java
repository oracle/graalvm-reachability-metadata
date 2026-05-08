/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.sts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.services.sts.endpoints.internal.DefaultPartitionDataProvider;
import software.amazon.awssdk.services.sts.endpoints.internal.Partitions;

public class DefaultPartitionDataProviderTest {
    @Test
    void loadPartitionsLooksForClasspathPartitionMetadataBeforeUsingGeneratedFallback() {
        String partitionsFileProperty = SdkSystemSetting.AWS_PARTITIONS_FILE.property();
        String previousPartitionsFile = System.clearProperty(partitionsFileProperty);
        try {
            Partitions partitions = new DefaultPartitionDataProvider().loadPartitions();
            Set<String> partitionIds = partitions.partitions().stream()
                    .map(partition -> partition.id())
                    .collect(Collectors.toSet());

            assertThat(partitions.version()).isNotBlank();
            assertThat(partitionIds).contains("aws", "aws-cn", "aws-us-gov");
        } finally {
            if (previousPartitionsFile != null) {
                System.setProperty(partitionsFileProperty, previousPartitionsFile);
            }
        }
    }
}
