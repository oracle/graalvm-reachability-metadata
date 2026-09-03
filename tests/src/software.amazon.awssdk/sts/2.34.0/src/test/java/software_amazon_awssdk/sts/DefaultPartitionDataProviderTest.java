/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.sts;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.endpoints.Endpoint;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.endpoints.StsEndpointParams;
import software.amazon.awssdk.services.sts.endpoints.StsEndpointProvider;

public class DefaultPartitionDataProviderTest {
    @Test
    void resolvesRegionalEndpointUsingDefaultPartitionMetadata() throws Exception {
        StsEndpointParams params = StsEndpointParams.builder()
                .region(Region.of("eusc-de-east-1"))
                .useDualStack(false)
                .useFips(false)
                .useGlobalEndpoint(false)
                .build();

        Endpoint endpoint = StsEndpointProvider.defaultProvider()
                .resolveEndpoint(params)
                .get(10, TimeUnit.SECONDS);

        assertThat(endpoint.url()).isEqualTo(URI.create("https://sts.eusc-de-east-1.amazonaws.eu"));
    }
}
