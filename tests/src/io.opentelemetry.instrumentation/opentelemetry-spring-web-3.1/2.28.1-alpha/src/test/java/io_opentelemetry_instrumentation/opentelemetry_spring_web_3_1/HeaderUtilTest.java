/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_spring_web_3_1;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.web.v3_1.SpringWebTelemetry;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class HeaderUtilTest {
    @Test
    void interceptorProcessesRequestsWithConfiguredCapturedHeaders() throws Exception {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.add("X-Test-Request", "request-value");
        TestHttpRequest request = new TestHttpRequest(
                HttpMethod.GET,
                URI.create("https://example.test:8443/widgets?debug=true"),
                requestHeaders);
        TestClientHttpResponse response = new TestClientHttpResponse(HttpStatus.ACCEPTED);
        response.getHeaders().add("X-Test-Response", "response-value");
        byte[] body = "payload".getBytes(UTF_8);
        AtomicInteger executionCount = new AtomicInteger();
        AtomicReference<HttpRequest> executedRequest = new AtomicReference<>();
        AtomicReference<byte[]> executedBody = new AtomicReference<>();

        ClientHttpRequestInterceptor interceptor = SpringWebTelemetry.builder(OpenTelemetry.noop())
                .setCapturedRequestHeaders(List.of("X-Test-Request"))
                .setCapturedResponseHeaders(List.of("X-Test-Response"))
                .build()
                .createInterceptor();

        ClientHttpResponse interceptedResponse = interceptor.intercept(
                request,
                body,
                (actualRequest, actualBody) -> {
                    executionCount.incrementAndGet();
                    executedRequest.set(actualRequest);
                    executedBody.set(actualBody);
                    assertThat(actualRequest.getHeaders().get("X-Test-Request"))
                            .containsExactly("request-value");
                    return response;
                });

        assertThat(interceptedResponse).isSameAs(response);
        assertThat(executionCount).hasValue(1);
        assertThat(executedRequest.get()).isSameAs(request);
        assertThat(executedBody.get()).isEqualTo(body);
        assertThat(interceptedResponse.getHeaders().get("X-Test-Response")).containsExactly("response-value");
    }

    private static final class TestHttpRequest implements HttpRequest {
        private final HttpMethod method;
        private final URI uri;
        private final HttpHeaders headers;

        private TestHttpRequest(HttpMethod method, URI uri, HttpHeaders headers) {
            this.method = method;
            this.uri = uri;
            this.headers = headers;
        }

        @Override
        public HttpMethod getMethod() {
            return method;
        }

        @Override
        public URI getURI() {
            return uri;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }

    private static final class TestClientHttpResponse implements ClientHttpResponse {
        private final HttpStatus status;
        private final HttpHeaders headers = new HttpHeaders();

        private TestClientHttpResponse(HttpStatus status) {
            this.status = status;
        }

        @Override
        public HttpStatus getStatusCode() {
            return status;
        }

        @Override
        public String getStatusText() {
            return status.getReasonPhrase();
        }

        @Override
        public void close() {
            headers.clear();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
