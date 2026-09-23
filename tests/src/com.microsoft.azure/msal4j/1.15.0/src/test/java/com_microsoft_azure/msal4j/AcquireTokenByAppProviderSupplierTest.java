/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_azure.msal4j;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.aad.msal4j.AppTokenProviderParameters;
import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.TokenProviderResult;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class AcquireTokenByAppProviderSupplierTest {
    private static final Set<String> SCOPES = Set.of("api://resource/.default");

    @Test
    void acquiresTokenFromApplicationTokenProvider() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<AppTokenProviderParameters> receivedParameters = new AtomicReference<>();
        long expiresOn = Instant.now().plusSeconds(3600).getEpochSecond();

        try {
            ConfidentialClientApplication application = ConfidentialClientApplication.builder(
                            "client-id", ClientCredentialFactory.createFromSecret("unused-secret"))
                    .authority("https://login.microsoftonline.com/default-tenant")
                    .validateAuthority(false)
                    .instanceDiscovery(false)
                    .correlationId("00000000-0000-0000-0000-000000000123")
                    .executorService(executor)
                    .appTokenProvider(parameters -> {
                        receivedParameters.set(parameters);
                        TokenProviderResult providerResult = new TokenProviderResult();
                        providerResult.setAccessToken("provider-access-token");
                        providerResult.setTenantId("request-tenant");
                        providerResult.setExpiresInSeconds(expiresOn);
                        return CompletableFuture.completedFuture(providerResult);
                    })
                    .build();
            ClientCredentialParameters parameters = ClientCredentialParameters.builder(SCOPES)
                    .tenant("request-tenant")
                    .skipCache(true)
                    .build();

            IAuthenticationResult result = application.acquireToken(parameters).get(10, SECONDS);

            assertThat(result.accessToken()).isEqualTo("provider-access-token");
            assertThat(result.expiresOnDate()).hasTime(expiresOn * 1000);
            assertThat(receivedParameters.get()).isNotNull();
            assertThat(receivedParameters.get().getScopes()).isEqualTo(SCOPES);
            assertThat(receivedParameters.get().getTenantId()).isEqualTo("request-tenant");
            assertThat(receivedParameters.get().getCorrelationId())
                    .isEqualTo("00000000-0000-0000-0000-000000000123");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
        }
    }
}
