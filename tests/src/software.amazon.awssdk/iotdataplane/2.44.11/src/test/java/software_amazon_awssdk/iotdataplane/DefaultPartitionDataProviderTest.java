/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.iotdataplane;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.endpoints.Endpoint;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iotdataplane.endpoints.IotDataPlaneEndpointParams;
import software.amazon.awssdk.services.iotdataplane.endpoints.IotDataPlaneEndpointProvider;

public class DefaultPartitionDataProviderTest {
    @Test
    void defaultEndpointProviderLoadsPartitionsWhenResolvingRegionalEndpoint() {
        String propertyName = SdkSystemSetting.AWS_PARTITIONS_FILE.property();
        String previousPartitionsFile = System.getProperty(propertyName);
        System.clearProperty(propertyName);

        try {
            IotDataPlaneEndpointParams endpointParams = IotDataPlaneEndpointParams.builder()
                    .region(Region.US_EAST_1)
                    .build();

            Endpoint endpoint = IotDataPlaneEndpointProvider.defaultProvider()
                    .resolveEndpoint(endpointParams)
                    .join();

            assertThat(endpoint.url())
                    .isEqualTo(URI.create("https://data-ats.iot.us-east-1.amazonaws.com"));
        } finally {
            if (previousPartitionsFile == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previousPartitionsFile);
            }
        }
    }
}
