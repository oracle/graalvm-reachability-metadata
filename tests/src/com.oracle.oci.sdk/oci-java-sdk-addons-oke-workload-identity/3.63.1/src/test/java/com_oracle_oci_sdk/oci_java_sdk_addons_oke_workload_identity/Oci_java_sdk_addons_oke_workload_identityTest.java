/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_addons_oke_workload_identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oracle.bmc.auth.ServiceAccountTokenSupplier;
import com.oracle.bmc.auth.okeworkloadidentity.OkeWorkloadIdentityAuthenticationDetailsProvider;
import com.oracle.bmc.auth.okeworkloadidentity.internal.GetOkeResourcePrincipalSessionTokenDetails;
import com.oracle.bmc.auth.okeworkloadidentity.internal.GetOkeResourcePrincipalSessionTokenRequest;
import com.oracle.bmc.auth.okeworkloadidentity.internal.OkeResourcePrincipalSessionToken;
import com.oracle.bmc.auth.okeworkloadidentity.internal.OkeTenancyOnlyAuthenticationDetailsProvider;
import com.oracle.bmc.http.client.RequestInterceptor;
import com.oracle.bmc.http.signing.RequestSigner;
import com.oracle.bmc.retrier.RetryConfiguration;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Oci_java_sdk_addons_oke_workload_identityTest {
    @Test
    void authenticationBuilderStoresEndpointAndTenancyConfigurationWithoutOpeningNetwork() {
        ServiceAccountTokenSupplier supplier = () -> "service-account-jwt";
        OkeWorkloadIdentityAuthenticationDetailsProvider
                        .OkeWorkloadIdentityAuthenticationDetailsProviderBuilder
                builder = OkeWorkloadIdentityAuthenticationDetailsProvider.builder();

        assertThat(builder.metadataBaseUrl("http://169.254.169.254/opc/v2/"))
                .isSameAs(builder);
        assertThat(builder.federationEndpoint("https://auth.example.com/v1/x509"))
                .isSameAs(builder);
        assertThat(builder.detectEndpointRetries(2)).isSameAs(builder);
        assertThat(builder.timeoutForEachRetry(1)).isSameAs(builder);
        assertThat(builder.tenancyId("ocid1.tenancy.oc1..exampleuniqueid"))
                .isSameAs(builder);
        assertThat(builder.token("literal-token")).isSameAs(builder);
        assertThat(builder.tokenPath("/var/run/secrets/tokens/oci-token")).isSameAs(builder);
        assertThat(builder.tokenPath(supplier)).isSameAs(builder);

        assertThat(builder.getMetadataBaseUrl()).isEqualTo("http://169.254.169.254/opc/v2/");
        assertThat(builder.getFederationEndpoint()).isEqualTo("https://auth.example.com/v1/x509");
        assertThat(builder.getTenancyId()).isEqualTo("ocid1.tenancy.oc1..exampleuniqueid");
        assertThat(builder.getRegion()).isNull();
    }

    @Test
    void sessionTokenDetailsBuilderTracksExplicitPodKeyAndCopiesIt() {
        GetOkeResourcePrincipalSessionTokenDetails details =
                GetOkeResourcePrincipalSessionTokenDetails.builder().podKey("pod-key").build();

        assertThat(details.getPodKey()).isEqualTo("pod-key");
        assertThat(details.wasPropertyExplicitlySet("podKey")).isTrue();
        assertThat(details.wasPropertyExplicitlySet("missingProperty")).isFalse();
        assertThat(details).isEqualTo(new GetOkeResourcePrincipalSessionTokenDetails("pod-key"));
        assertThat(details.hashCode())
                .isEqualTo(new GetOkeResourcePrincipalSessionTokenDetails("pod-key").hashCode());

        GetOkeResourcePrincipalSessionTokenDetails copied = details.toBuilder().build();
        GetOkeResourcePrincipalSessionTokenDetails changed =
                details.toBuilder().podKey("other").build();

        assertThat(copied.getPodKey()).isEqualTo("pod-key");
        assertThat(copied.wasPropertyExplicitlySet("podKey")).isTrue();
        assertThat(changed.getPodKey()).isEqualTo("other");
        assertThat(changed).isNotEqualTo(details);
        assertThat(details).isNotEqualTo("pod-key");
    }

    @Test
    void sessionTokenRequestBuilderPreservesBodyCallbackAndRetryConfiguration() {
        GetOkeResourcePrincipalSessionTokenDetails details =
                GetOkeResourcePrincipalSessionTokenDetails.builder().podKey("pod-key").build();
        RequestInterceptor callback = request -> {};
        RetryConfiguration retryConfiguration = RetryConfiguration.NO_RETRY_CONFIGURATION;

        GetOkeResourcePrincipalSessionTokenRequest request =
                GetOkeResourcePrincipalSessionTokenRequest.builder()
                        .body$(details)
                        .invocationCallback(callback)
                        .retryConfiguration(retryConfiguration)
                        .build();

        assertThat(request.getGetOkeResourcePrincipalSessionTokenDetails()).isSameAs(details);
        assertThat(request.getBody$()).isSameAs(details);
        assertThat(request.getInvocationCallback()).isSameAs(callback);
        assertThat(request.getRetryConfiguration()).isSameAs(retryConfiguration);
        assertThat(request.toString())
                .contains("super=")
                .contains("getOkeResourcePrincipalSessionTokenDetails");

        GetOkeResourcePrincipalSessionTokenRequest copiedBody = request.toBuilder().build();
        assertThat(copiedBody.getGetOkeResourcePrincipalSessionTokenDetails()).isSameAs(details);
        assertThat(copiedBody.getInvocationCallback()).isNull();
        assertThat(copiedBody.getRetryConfiguration()).isNull();

        GetOkeResourcePrincipalSessionTokenRequest copiedRequest =
                GetOkeResourcePrincipalSessionTokenRequest.builder().copy(request).build();
        assertThat(copiedRequest.getGetOkeResourcePrincipalSessionTokenDetails()).isSameAs(details);
        assertThat(copiedRequest.getInvocationCallback()).isSameAs(callback);
        assertThat(copiedRequest.getRetryConfiguration()).isSameAs(retryConfiguration);

        GetOkeResourcePrincipalSessionTokenRequest withoutCallback =
                GetOkeResourcePrincipalSessionTokenRequest.builder()
                        .getOkeResourcePrincipalSessionTokenDetails(details)
                        .invocationCallback(callback)
                        .retryConfiguration(retryConfiguration)
                        .buildWithoutInvocationCallback();
        assertThat(withoutCallback.getGetOkeResourcePrincipalSessionTokenDetails())
                .isSameAs(details);
        assertThat(withoutCallback.getInvocationCallback()).isNull();
        assertThat(withoutCallback.getRetryConfiguration()).isNull();
    }

    @Test
    void resourcePrincipalSessionTokenExposesTokenFromConstructors() {
        OkeResourcePrincipalSessionToken emptyToken = new OkeResourcePrincipalSessionToken();
        OkeResourcePrincipalSessionToken sessionToken =
                new OkeResourcePrincipalSessionToken("rpst-token");

        assertThat(emptyToken.getToken()).isNull();
        assertThat(sessionToken.getToken()).isEqualTo("rpst-token");
    }

    @Test
    void tenancyOnlyProviderSuppliesNoOpSignerForUnsignedTokenExchangeRequests() {
        OkeTenancyOnlyAuthenticationDetailsProvider provider =
                new OkeTenancyOnlyAuthenticationDetailsProvider();

        assertThat(provider.getKeyId()).isNull();
        assertThat(provider.getPrivateKey()).isNull();
        assertThat(provider.getPassphraseCharacters()).isNull();
        assertThat(provider.refresh()).isNull();

        RequestSigner signer = provider.getCustomRequestSigner();
        Map<String, String> signedHeaders =
                signer.signRequest(
                        URI.create("https://auth.example.com/resourcePrincipalSessionToken"),
                        "POST",
                        Map.of("opc-request-id", List.of("request-1")),
                        "body");

        assertThat(signer).isSameAs(provider.getCustomRequestSigner());
        assertThat(signedHeaders).isEmpty();
        assertThatThrownBy(() -> signedHeaders.put("authorization", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
