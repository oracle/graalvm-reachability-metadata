/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.endpoints_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.endpoints.Endpoint;
import software.amazon.awssdk.endpoints.EndpointAttributeKey;
import software.amazon.awssdk.endpoints.EndpointProvider;

public class Endpoints_spiTest {
    private static final URI SERVICE_ENDPOINT = URI.create("https://service.us-east-1.amazonaws.com");
    private static final URI FIPS_ENDPOINT = URI.create("https://service-fips.us-east-1.amazonaws.com");

    @Test
    void builderStoresUrlHeadersAndTypedAttributes() {
        EndpointAttributeKey<Boolean> fipsKey = new EndpointAttributeKey<>("fips", Boolean.class);
        EndpointAttributeKey<String> signingRegionKey = new EndpointAttributeKey<>("signingRegion", String.class);
        EndpointAttributeKey<List<String>> authSchemesKey = EndpointAttributeKey.forList("authSchemes");

        Endpoint endpoint = Endpoint.builder()
                .url(SERVICE_ENDPOINT)
                .putHeader("x-amz-endpoint", "primary")
                .putHeader("x-amz-endpoint", "dualstack")
                .putHeader("x-amz-region", "us-east-1")
                .putAttribute(fipsKey, Boolean.FALSE)
                .putAttribute(signingRegionKey, "us-east-1")
                .putAttribute(authSchemesKey, List.of("sigv4", "sigv4a"))
                .build();

        assertThat(endpoint.url()).isEqualTo(SERVICE_ENDPOINT);
        assertThat(endpoint.headers())
                .containsEntry("x-amz-endpoint", List.of("primary", "dualstack"))
                .containsEntry("x-amz-region", List.of("us-east-1"));
        assertThat(endpoint.attribute(fipsKey)).isFalse();
        assertThat(endpoint.attribute(signingRegionKey)).isEqualTo("us-east-1");
        assertThat(endpoint.attribute(authSchemesKey)).containsExactly("sigv4", "sigv4a");
    }

    @Test
    void emptyEndpointHasNullUrlEmptyHeadersAndNoAttributes() {
        EndpointAttributeKey<String> missingKey = new EndpointAttributeKey<>("missing", String.class);

        Endpoint endpoint = Endpoint.builder().build();

        assertThat(endpoint.url()).isNull();
        assertThat(endpoint.headers()).isEmpty();
        assertThat(endpoint.attribute(missingKey)).isNull();
    }

    @Test
    void toBuilderCopiesExistingEndpointAndAllowsIndependentChanges() {
        EndpointAttributeKey<String> signingNameKey = new EndpointAttributeKey<>("signingName", String.class);
        Endpoint original = Endpoint.builder()
                .url(SERVICE_ENDPOINT)
                .putHeader("x-amz-mode", "standard")
                .putAttribute(signingNameKey, "service")
                .build();

        Endpoint modified = original.toBuilder()
                .url(FIPS_ENDPOINT)
                .putHeader("x-amz-mode", "fips")
                .putAttribute(signingNameKey, "service-fips")
                .build();

        assertThat(original.url()).isEqualTo(SERVICE_ENDPOINT);
        assertThat(original.headers()).containsEntry("x-amz-mode", List.of("standard"));
        assertThat(original.attribute(signingNameKey)).isEqualTo("service");

        assertThat(modified.url()).isEqualTo(FIPS_ENDPOINT);
        assertThat(modified.headers()).containsEntry("x-amz-mode", List.of("standard", "fips"));
        assertThat(modified.attribute(signingNameKey)).isEqualTo("service-fips");
    }

    @Test
    void endpointsCompareByUrlHeadersAndAttributes() {
        EndpointAttributeKey<Integer> priorityKey = new EndpointAttributeKey<>("priority", Integer.class);
        Endpoint first = Endpoint.builder()
                .url(SERVICE_ENDPOINT)
                .putHeader("x-amz-region", "us-east-1")
                .putAttribute(priorityKey, 1)
                .build();
        Endpoint equal = Endpoint.builder()
                .url(SERVICE_ENDPOINT)
                .putHeader("x-amz-region", "us-east-1")
                .putAttribute(new EndpointAttributeKey<>("priority", Integer.class), 1)
                .build();
        Endpoint differentUrl = Endpoint.builder()
                .url(FIPS_ENDPOINT)
                .putHeader("x-amz-region", "us-east-1")
                .putAttribute(priorityKey, 1)
                .build();
        Endpoint differentHeader = Endpoint.builder()
                .url(SERVICE_ENDPOINT)
                .putHeader("x-amz-region", "us-west-2")
                .putAttribute(priorityKey, 1)
                .build();
        Endpoint differentAttribute = Endpoint.builder()
                .url(SERVICE_ENDPOINT)
                .putHeader("x-amz-region", "us-east-1")
                .putAttribute(priorityKey, 2)
                .build();

        assertThat(first)
                .isEqualTo(first)
                .isEqualTo(equal)
                .isNotEqualTo(null)
                .isNotEqualTo("not an endpoint")
                .isNotEqualTo(differentUrl)
                .isNotEqualTo(differentHeader)
                .isNotEqualTo(differentAttribute);
        assertThat(first.hashCode()).isEqualTo(equal.hashCode());
    }

    @Test
    void endpointAttributeKeysCompareByNameAndValueClass() {
        EndpointAttributeKey<String> signingName = new EndpointAttributeKey<>("signingName", String.class);
        EndpointAttributeKey<String> sameSigningName = new EndpointAttributeKey<>("signingName", String.class);
        EndpointAttributeKey<Integer> differentClass = new EndpointAttributeKey<>("signingName", Integer.class);
        EndpointAttributeKey<String> differentName = new EndpointAttributeKey<>("signingRegion", String.class);
        EndpointAttributeKey<List<String>> listKey = EndpointAttributeKey.forList("authSchemes");
        EndpointAttributeKey<List<Integer>> sameListKeyWithDifferentGenericType =
                EndpointAttributeKey.forList("authSchemes");

        assertThat(signingName)
                .isEqualTo(signingName)
                .isEqualTo(sameSigningName)
                .isNotEqualTo(null)
                .isNotEqualTo("signingName")
                .isNotEqualTo(differentClass)
                .isNotEqualTo(differentName);
        assertThat(signingName.hashCode()).isEqualTo(sameSigningName.hashCode());
        assertThat(listKey.hashCode()).isEqualTo(sameListKeyWithDifferentGenericType.hashCode());
        assertThat(listKey).isEqualTo(sameListKeyWithDifferentGenericType);
    }

    @Test
    void equalAttributeKeysCanReadStoredValues() {
        EndpointAttributeKey<String> writeKey = new EndpointAttributeKey<>("signingRegion", String.class);
        EndpointAttributeKey<String> equivalentReadKey = new EndpointAttributeKey<>("signingRegion", String.class);
        EndpointAttributeKey<String> unknownKey = new EndpointAttributeKey<>("unknown", String.class);

        Endpoint endpoint = Endpoint.builder()
                .putAttribute(writeKey, "us-east-1")
                .build();

        assertThat(endpoint.attribute(equivalentReadKey)).isEqualTo("us-east-1");
        assertThat(endpoint.attribute(unknownKey)).isNull();
    }

    @Test
    void putAttributeReplacesExistingValueForEquivalentKey() {
        EndpointAttributeKey<String> initialSigningRegionKey = new EndpointAttributeKey<>("signingRegion", String.class);
        EndpointAttributeKey<String> replacementSigningRegionKey = new EndpointAttributeKey<>("signingRegion", String.class);

        Endpoint endpoint = Endpoint.builder()
                .putAttribute(initialSigningRegionKey, "us-east-1")
                .putAttribute(replacementSigningRegionKey, "us-west-2")
                .build();

        assertThat(endpoint.attribute(initialSigningRegionKey)).isEqualTo("us-west-2");
        assertThat(endpoint.attribute(replacementSigningRegionKey)).isEqualTo("us-west-2");
    }

    @Test
    void attributesWithSameNameAndDifferentValueTypesDoNotCollide() {
        EndpointAttributeKey<String> stringTimeoutKey = new EndpointAttributeKey<>("timeout", String.class);
        EndpointAttributeKey<Integer> integerTimeoutKey = new EndpointAttributeKey<>("timeout", Integer.class);

        Endpoint endpoint = Endpoint.builder()
                .putAttribute(stringTimeoutKey, "standard")
                .putAttribute(integerTimeoutKey, 30)
                .build();

        assertThat(endpoint.attribute(stringTimeoutKey)).isEqualTo("standard");
        assertThat(endpoint.attribute(integerTimeoutKey)).isEqualTo(30);
    }

    @Test
    void endpointProviderIsUsableAsPublicMarkerInterface() {
        EndpointAttributeKey<String> resolverKey = new EndpointAttributeKey<>("resolver", String.class);
        EndpointProvider provider = new StaticEndpointProvider(Endpoint.builder()
                .url(SERVICE_ENDPOINT)
                .putAttribute(resolverKey, "static")
                .build());

        assertThat(provider).isInstanceOf(EndpointProvider.class);
        assertThat(((StaticEndpointProvider) provider).endpoint().attribute(resolverKey)).isEqualTo("static");
    }

    private static final class StaticEndpointProvider implements EndpointProvider {
        private final Endpoint endpoint;

        private StaticEndpointProvider(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        private Endpoint endpoint() {
            return endpoint;
        }
    }
}
