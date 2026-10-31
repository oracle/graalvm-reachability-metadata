/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_openai.openai_java_core;

import com.openai.core.ClientOptions;
import com.openai.core.DefaultSleeper;
import com.openai.core.LogLevel;
import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpMethod;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhantomReachableInnerCloseWhenPhantomReachableAnonymous5Test {
    @Test
    void configuredClientDelegatesRequestsAndClosesOwnedTransport() throws Exception {
        RecordingHttpClient transport = new RecordingHttpClient();
        try (transport; DefaultSleeper sleeper = new DefaultSleeper()) {
            ClientOptions options = ClientOptions.builder()
                    .httpClient(transport)
                    .apiKey("test-key")
                    .streamHandlerExecutor(Runnable::run)
                    .sleeper(sleeper)
                    .logLevel(LogLevel.OFF)
                    .maxRetries(0)
                    .build();
            HttpRequest request = HttpRequest.builder()
                    .method(HttpMethod.GET)
                    .baseUrl("https://example.invalid")
                    .addPathSegment("messages")
                    .build();

            try (HttpClient client = options.httpClient()) {
                try (HttpResponse response = client.execute(request, RequestOptions.none())) {
                    assertEquals(200, response.statusCode());
                    assertEquals("request-local", response.requestId().orElseThrow());
                    assertEquals("message received",
                            new String(response.body().readAllBytes(), StandardCharsets.UTF_8));
                }
                assertEquals("https://example.invalid/messages", transport.requestUrl);
                assertTrue(transport.responseClosed);
            }
            assertTrue(transport.closed);
        }
    }

    private static final class RecordingHttpClient implements HttpClient {
        private String requestUrl;
        private boolean responseClosed;
        private boolean closed;

        @Override
        public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
            requestUrl = request.url();
            return new HttpResponse() {
                private final InputStream body = new ByteArrayInputStream(
                        "message received".getBytes(StandardCharsets.UTF_8));

                @Override
                public int statusCode() {
                    return 200;
                }

                @Override
                public Headers headers() {
                    return Headers.builder().put("x-request-id", "request-local").build();
                }

                @Override
                public InputStream body() {
                    return body;
                }

                @Override
                public void close() {
                    responseClosed = true;
                }
            };
        }

        @Override
        public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions requestOptions) {
            return CompletableFuture.completedFuture(execute(request, requestOptions));
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
