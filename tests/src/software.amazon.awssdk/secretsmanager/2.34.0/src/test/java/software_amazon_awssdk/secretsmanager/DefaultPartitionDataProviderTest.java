/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.secretsmanager;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.services.secretsmanager.endpoints.internal.DefaultPartitionDataProvider;
import software.amazon.awssdk.services.secretsmanager.endpoints.internal.Partition;
import software.amazon.awssdk.services.secretsmanager.endpoints.internal.Partitions;

public class DefaultPartitionDataProviderTest {
    @Test
    void loadsPartitionData() {
        String partitionsFileProperty = SdkSystemSetting.AWS_PARTITIONS_FILE.property();
        String previousPartitionsFile = System.getProperty(partitionsFileProperty);
        System.clearProperty(partitionsFileProperty);

        try {
            DefaultPartitionDataProvider provider = new DefaultPartitionDataProvider();

            Partitions partitions = provider.loadPartitions();

            assertThat(partitions.version()).isNotBlank();
            assertThat(partitions.partitions())
                    .extracting(Partition::id)
                    .contains("aws", "aws-cn", "aws-us-gov");
        } finally {
            if (previousPartitionsFile == null) {
                System.clearProperty(partitionsFileProperty);
            } else {
                System.setProperty(partitionsFileProperty, previousPartitionsFile);
            }
        }
    }
}
