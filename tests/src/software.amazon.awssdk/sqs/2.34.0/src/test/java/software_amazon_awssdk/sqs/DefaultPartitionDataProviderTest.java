/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.sqs;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.endpoints.Endpoint;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.endpoints.SqsEndpointParams;
import software.amazon.awssdk.services.sqs.endpoints.SqsEndpointProvider;

public class DefaultPartitionDataProviderTest {
    @Test
    void defaultEndpointProviderLoadsBundledPartitionMetadata() throws Exception {
        String propertyName = SdkSystemSetting.AWS_PARTITIONS_FILE.property();
        String originalPropertyValue = System.getProperty(propertyName);
        System.clearProperty(propertyName);

        try {
            SqsEndpointParams params = SqsEndpointParams.builder()
                    .region(Region.US_WEST_2)
                    .build();

            Endpoint endpoint = SqsEndpointProvider.defaultProvider()
                    .resolveEndpoint(params)
                    .get(10, TimeUnit.SECONDS);

            assertThat(endpoint.url()).isEqualTo(URI.create("https://sqs.us-west-2.amazonaws.com"));
        } finally {
            if (originalPropertyValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, originalPropertyValue);
            }
        }
    }
}
