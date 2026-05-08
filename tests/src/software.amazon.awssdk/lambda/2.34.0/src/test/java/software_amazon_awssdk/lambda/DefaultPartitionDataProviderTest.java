/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.endpoints.internal.DefaultPartitionDataProvider;
import software.amazon.awssdk.services.lambda.endpoints.internal.Partition;
import software.amazon.awssdk.services.lambda.endpoints.internal.Partitions;

public class DefaultPartitionDataProviderTest {
    @Test
    void loadsPartitionsFromBundledClasspathMetadata() {
        String previousPartitionsFile = System.clearProperty("aws.partitionsFile");
        try {
            Partitions partitions = new DefaultPartitionDataProvider().loadPartitions();

            assertNotNull(partitions);
            assertEquals("1.1", partitions.version());
            assertFalse(partitions.partitions().isEmpty());

            Partition awsPartition = partitions.partitions().stream()
                    .filter(partition -> "aws".equals(partition.id()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(awsPartition.regions().containsKey("us-east-1"));
            assertTrue(awsPartition.regionMatches("us-west-2"));
            assertEquals("amazonaws.com", awsPartition.outputs().dnsSuffix());
        } finally {
            if (previousPartitionsFile == null) {
                System.clearProperty("aws.partitionsFile");
            } else {
                System.setProperty("aws.partitionsFile", previousPartitionsFile);
            }
        }
    }
}
