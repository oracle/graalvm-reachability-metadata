/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.cognitoidentity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.endpoints.Endpoint;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentity.endpoints.CognitoIdentityEndpointParams;
import software.amazon.awssdk.services.cognitoidentity.endpoints.CognitoIdentityEndpointProvider;

public class DefaultPartitionDataProviderTest {
    @Test
    void defaultEndpointResolutionLoadsPartitionDataFromClasspath() {
        String propertyName = SdkSystemSetting.AWS_PARTITIONS_FILE.property();
        String previousPartitionsFile = System.getProperty(propertyName);
        System.clearProperty(propertyName);

        try {
            CognitoIdentityEndpointParams params = CognitoIdentityEndpointParams.builder()
                    .region(Region.US_EAST_1)
                    .build();

            Endpoint endpoint = CognitoIdentityEndpointProvider.defaultProvider()
                    .resolveEndpoint(params)
                    .join();

            assertThat(endpoint.url()).isEqualTo(URI.create("https://cognito-identity.us-east-1.amazonaws.com"));
        } finally {
            if (previousPartitionsFile == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previousPartitionsFile);
            }
        }
    }
}
