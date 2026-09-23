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

import com.microsoft.aad.msal4j.AuthorizationRequestUrlParameters;
import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.HttpMethod;
import com.microsoft.aad.msal4j.HttpRequest;
import com.microsoft.aad.msal4j.HttpResponse;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.IHttpClient;
import com.microsoft.aad.msal4j.IHttpResponse;
import com.microsoft.aad.msal4j.Prompt;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.ResponseMode;
import com.microsoft.aad.msal4j.TokenSource;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class Msal4jTest {
    private static final String AUTHORITY = "https://login.microsoftonline.com/tenant-id";
    private static final Set<String> SCOPES = Set.of("api://resource/.default");

    @Test
    void acquiresAndCachesClientCredentialToken() throws Exception {
        RecordingTokenClient httpClient = new RecordingTokenClient();
        ConfidentialClientApplication application = newConfidentialApplication(httpClient);
        ClientCredentialParameters parameters = ClientCredentialParameters.builder(SCOPES).build();

        IAuthenticationResult identityProviderResult = application.acquireToken(parameters).get(10, SECONDS);

        assertThat(identityProviderResult.accessToken()).isEqualTo("access-token");
        assertThat(identityProviderResult.environment()).isEqualTo("login.microsoftonline.com");
        assertThat(identityProviderResult.scopes()).contains("api://resource/.default");
        assertThat(identityProviderResult.expiresOnDate()).isAfter(new Date());
        assertThat(identityProviderResult.metadata().tokenSource()).isEqualTo(TokenSource.IDENTITY_PROVIDER);
        assertThat(httpClient.requests).hasSize(1);
        HttpRequest tokenRequest = httpClient.requests.get(0);
        assertThat(tokenRequest.httpMethod()).isEqualTo(HttpMethod.POST);
        assertThat(tokenRequest.url().toString()).isEqualTo(AUTHORITY + "/oauth2/v2.0/token");
        assertThat(tokenRequest.body())
                .contains("client_id=client-id")
                .contains("client_secret=client-secret")
                .contains("grant_type=client_credentials")
                .contains("scope=")
                .contains("api%3A%2F%2Fresource%2F.default");

        IAuthenticationResult cachedResult = application.acquireToken(parameters).get(10, SECONDS);

        assertThat(cachedResult.accessToken()).isEqualTo("access-token");
        assertThat(httpClient.requests).hasSize(1);

        String serializedCache = application.tokenCache().serialize();
        assertThat(serializedCache).contains("AccessToken").contains("access-token");

        RecordingTokenClient restoredHttpClient = new RecordingTokenClient();
        ConfidentialClientApplication restoredApplication = newConfidentialApplication(restoredHttpClient);
        restoredApplication.tokenCache().deserialize(serializedCache);

        IAuthenticationResult restoredResult = restoredApplication.acquireToken(parameters).get(10, SECONDS);

        assertThat(restoredResult.accessToken()).isEqualTo("access-token");
        assertThat(restoredHttpClient.requests).isEmpty();
    }

    @Test
    void createsAuthorizationCodeRequestUrl() throws Exception {
        PublicClientApplication application = PublicClientApplication.builder("public-client-id")
                .authority(AUTHORITY)
                .validateAuthority(false)
                .instanceDiscovery(false)
                .build();
        AuthorizationRequestUrlParameters parameters = AuthorizationRequestUrlParameters.builder(
                        "https://application.example.test/callback", Set.of("openid", "profile"))
                .state("state-value")
                .nonce("nonce-value")
                .loginHint("user@example.test")
                .prompt(Prompt.SELECT_ACCOUNT)
                .responseMode(ResponseMode.QUERY)
                .build();

        URL authorizationUrl = application.getAuthorizationRequestUrl(parameters);
        String decodedQuery = URLDecoder.decode(authorizationUrl.getQuery(), UTF_8);

        assertThat(authorizationUrl.getProtocol()).isEqualTo("https");
        assertThat(authorizationUrl.getHost()).isEqualTo("login.microsoftonline.com");
        assertThat(authorizationUrl.getPath()).isEqualTo("/tenant-id/oauth2/v2.0/authorize");
        assertThat(decodedQuery)
                .contains("client_id=public-client-id")
                .contains("response_type=code")
                .contains("redirect_uri=https://application.example.test/callback")
                .contains("scope=")
                .contains("openid")
                .contains("profile")
                .contains("state=state-value")
                .contains("nonce=nonce-value")
                .contains("login_hint=user@example.test")
                .contains("prompt=select_account")
                .contains("response_mode=query");
    }

    private static ConfidentialClientApplication newConfidentialApplication(IHttpClient httpClient)
            throws Exception {
        return ConfidentialClientApplication.builder(
                        "client-id", ClientCredentialFactory.createFromSecret("client-secret"))
                .authority(AUTHORITY)
                .validateAuthority(false)
                .instanceDiscovery(false)
                .httpClient(httpClient)
                .applicationName("metadata-test")
                .applicationVersion("test")
                .build();
    }

    private static final class RecordingTokenClient implements IHttpClient {
        private static final String TOKEN_RESPONSE = """
                {
                  "token_type": "Bearer",
                  "scope": "api://resource/.default",
                  "expires_in": 3600,
                  "ext_expires_in": 3600,
                  "access_token": "access-token"
                }
                """;

        private final List<HttpRequest> requests = new ArrayList<>();

        @Override
        public IHttpResponse send(HttpRequest request) {
            requests.add(request);
            HttpResponse response = new HttpResponse().statusCode(200).body(TOKEN_RESPONSE);
            response.addHeaders(Map.of("Content-Type", List.of("application/json")));
            return response;
        }
    }
}
