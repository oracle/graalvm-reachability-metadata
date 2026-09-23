/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_azure.msal4j;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.aad.msal4j.HttpRequest;
import com.microsoft.aad.msal4j.HttpResponse;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.IHttpClient;
import com.microsoft.aad.msal4j.IHttpResponse;
import com.microsoft.aad.msal4j.ManagedIdentityApplication;
import com.microsoft.aad.msal4j.ManagedIdentityId;
import com.microsoft.aad.msal4j.ManagedIdentityParameters;
import java.net.URLDecoder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class ManagedIdentityResponseTest {
    private static final String RESOURCE = "https://management.azure.com/";

    @Test
    void acquiresTokenForSystemAssignedManagedIdentity() throws Exception {
        RecordingManagedIdentityClient httpClient = new RecordingManagedIdentityClient();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            ManagedIdentityApplication application =
                    ManagedIdentityApplication.builder(ManagedIdentityId.systemAssigned())
                            .httpClient(httpClient)
                            .executorService(executor)
                            .build();
            ManagedIdentityParameters parameters =
                    ManagedIdentityParameters.builder(RESOURCE).forceRefresh(true).build();

            IAuthenticationResult result =
                    application.acquireTokenForManagedIdentity(parameters).get(10, SECONDS);

            assertThat(result.accessToken()).isEqualTo("managed-identity-token");
            assertThat(result.scopes()).isEqualTo(RESOURCE);
            assertThat(result.expiresOnDate()).isAfter(new Date());
            assertThat(httpClient.requests).hasSize(1);

            HttpRequest request = httpClient.requests.get(0);
            assertThat(request.httpMethod().name()).isIn("GET", "POST");
            String requestParameters = URLDecoder.decode(
                    String.valueOf(request.url().getQuery()) + "&" + String.valueOf(request.body()), UTF_8);
            assertThat(requestParameters).contains("resource=" + RESOURCE);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
        }
    }

    private static final class RecordingManagedIdentityClient implements IHttpClient {
        private final List<HttpRequest> requests = new ArrayList<>();

        @Override
        public IHttpResponse send(HttpRequest request) {
            requests.add(request);
            String responseBody = """
                    {
                      "token_type": "Bearer",
                      "access_token": "managed-identity-token",
                      "expires_on": "%d",
                      "resource": "%s"
                    }
                    """.formatted(Instant.now().plusSeconds(3600).getEpochSecond(), RESOURCE);
            HttpResponse response = new HttpResponse().statusCode(200).body(responseBody);
            response.addHeaders(Map.of("Content-Type", List.of("application/json")));
            return response;
        }
    }
}
