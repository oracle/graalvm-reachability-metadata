/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_identity;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.util.Configuration;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Timeout(60)
public class ManagedIdentityResourceIdTest {
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(10);
    private static final String SCOPE = "https://management.azure.com/.default";
    private static final String RESOURCE_ID =
            "/subscriptions/subscription-id/resourceGroups/identity-group/providers/Microsoft.ManagedIdentity/userAssignedIdentities/test-identity";

    @Test
    void acquiresTokenForUserAssignedIdentityResource() {
        ManagedIdentityHttpClient httpClient = new ManagedIdentityHttpClient();
        ManagedIdentityCredential credential = new ManagedIdentityCredentialBuilder()
                .resourceId(RESOURCE_ID)
                .configuration(Configuration.NONE)
                .httpClient(httpClient)
                .build();

        AccessToken token = credential
                .getToken(new TokenRequestContext().addScopes(SCOPE))
                .block(IO_TIMEOUT);

        assertThat(token).isNotNull();
        assertThat(token.getToken()).isEqualTo("resource-id-access-token");
        assertThat(token.getTokenType()).isEqualTo("Bearer");
        assertThat(token.getExpiresAt()).isAfter(OffsetDateTime.now().plusMinutes(30));
        assertThat(httpClient.requests()).hasSize(1);

        HttpRequest request = httpClient.requests().get(0);
        assertThat(request.getHttpMethod().name()).isEqualTo("GET");
        assertThat(request.getHeaders().getValue("Metadata")).isEqualTo("true");
        assertThat(URLDecoder.decode(request.getUrl().getQuery(), UTF_8))
                .contains("api-version=")
                .contains("resource=https://management.azure.com")
                .contains(RESOURCE_ID);
    }

    private static final class ManagedIdentityHttpClient implements HttpClient {
        private static final String RESPONSE = """
                {
                  "token_type": "Bearer",
                  "resource": "https://management.azure.com",
                  "expires_in": "3600",
                  "expires_on": "4102444800",
                  "not_before": "1700000000",
                  "access_token": "resource-id-access-token"
                }
                """;

        private final List<HttpRequest> requests = new ArrayList<>();

        @Override
        public Mono<HttpResponse> send(HttpRequest request) {
            requests.add(request);
            return Mono.just(new ManagedIdentityHttpResponse(request, RESPONSE));
        }

        List<HttpRequest> requests() {
            return requests;
        }
    }

    private static final class ManagedIdentityHttpResponse extends HttpResponse {
        private final byte[] body;
        private final HttpHeaders headers;

        private ManagedIdentityHttpResponse(HttpRequest request, String body) {
            super(request);
            this.body = body.getBytes(UTF_8);
            this.headers = new HttpHeaders()
                    .set("Content-Type", "application/json")
                    .set("Content-Length", Integer.toString(this.body.length));
        }

        @Override
        public int getStatusCode() {
            return 200;
        }

        @Override
        public String getHeaderValue(String name) {
            return headers.getValue(name);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public Flux<ByteBuffer> getBody() {
            return Flux.defer(() -> Flux.just(ByteBuffer.wrap(body)));
        }

        @Override
        public Mono<byte[]> getBodyAsByteArray() {
            return Mono.just(body.clone());
        }

        @Override
        public Mono<String> getBodyAsString() {
            return Mono.just(new String(body, UTF_8));
        }

        @Override
        public Mono<String> getBodyAsString(Charset charset) {
            return Mono.just(new String(body, charset));
        }
    }
}
