/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auth.google_auth_library_oauth2_http;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.UserCredentials;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MetricsUtilsTest {
    private static final String API_CLIENT_HEADER = "x-goog-api-client";
    private static final URI TOKEN_SERVER_URI = URI.create("https://oauth2.example.test/token");

    @Test
    public void refreshAccessTokenSendsLibraryVersionMetricsHeader() throws IOException {
        CapturingHttpTransport transport = new CapturingHttpTransport("""
                {
                  "access_token": "refreshed-access-token",
                  "expires_in": 3600,
                  "scope": "email profile"
                }
                """);
        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId("client-id")
                .setClientSecret("client-secret")
                .setRefreshToken("refresh-token")
                .setTokenServerUri(TOKEN_SERVER_URI)
                .setHttpTransportFactory(new FixedHttpTransportFactory(transport))
                .build();

        AccessToken accessToken = credentials.refreshAccessToken();

        assertThat(accessToken.getTokenValue()).isEqualTo("refreshed-access-token");
        assertThat(accessToken.getScopes()).containsExactly("email", "profile");
        assertThat(transport.getRequestMethod()).isEqualTo("POST");
        assertThat(transport.getRequestUrl()).isEqualTo(TOKEN_SERVER_URI.toString());
        assertThat(transport.getRequestBody())
                .contains("client_id=client-id")
                .contains("client_secret=client-secret")
                .contains("refresh_token=refresh-token")
                .contains("grant_type=refresh_token");
        assertThat(transport.getHeaderValues(API_CLIENT_HEADER))
                .singleElement()
                .satisfies(header -> assertThat(header)
                        .contains("gl-java/", "auth/")
                        .doesNotContain("auth/unknown-version"));
    }

    private static final class FixedHttpTransportFactory implements HttpTransportFactory {
        private final HttpTransport transport;

        private FixedHttpTransportFactory(HttpTransport transport) {
            this.transport = transport;
        }

        @Override
        public HttpTransport create() {
            return transport;
        }
    }

    private static final class CapturingHttpTransport extends HttpTransport {
        private final String responseBody;
        private final Map<String, List<String>> requestHeaders = new HashMap<>();
        private String requestMethod;
        private String requestUrl;
        private String requestBody;

        private CapturingHttpTransport(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        protected LowLevelHttpRequest buildRequest(String method, String url) {
            requestMethod = method;
            requestUrl = url;
            return new LowLevelHttpRequest() {
                @Override
                public void addHeader(String name, String value) {
                    requestHeaders.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
                }

                @Override
                public LowLevelHttpResponse execute() throws IOException {
                    ByteArrayOutputStream content = new ByteArrayOutputStream();
                    if (getStreamingContent() != null) {
                        getStreamingContent().writeTo(content);
                    }
                    requestBody = content.toString(StandardCharsets.UTF_8);
                    return new JsonLowLevelHttpResponse(responseBody);
                }
            };
        }

        private String getRequestMethod() {
            return requestMethod;
        }

        private String getRequestUrl() {
            return requestUrl;
        }

        private String getRequestBody() {
            return requestBody;
        }

        private List<String> getHeaderValues(String name) {
            for (Map.Entry<String, List<String>> entry : requestHeaders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
            return List.of();
        }
    }

    private static final class JsonLowLevelHttpResponse extends LowLevelHttpResponse {
        private final byte[] content;

        private JsonLowLevelHttpResponse(String responseBody) {
            content = responseBody.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getContent() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public String getContentEncoding() {
            return null;
        }

        @Override
        public long getContentLength() {
            return content.length;
        }

        @Override
        public String getContentType() {
            return "application/json; charset=utf-8";
        }

        @Override
        public String getStatusLine() {
            return "HTTP/1.1 200 OK";
        }

        @Override
        public int getStatusCode() {
            return 200;
        }

        @Override
        public String getReasonPhrase() {
            return "OK";
        }

        @Override
        public int getHeaderCount() {
            return 0;
        }

        @Override
        public String getHeaderName(int index) {
            return null;
        }

        @Override
        public String getHeaderValue(int index) {
            return null;
        }
    }
}
