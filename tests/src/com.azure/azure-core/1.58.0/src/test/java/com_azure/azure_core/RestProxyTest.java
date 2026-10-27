/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.annotation.ExpectedResponses;
import com.azure.core.annotation.Get;
import com.azure.core.annotation.Host;
import com.azure.core.annotation.ServiceInterface;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.HttpPipelineBuilder;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.RestProxy;
import com.azure.core.util.serializer.JacksonAdapter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Timeout(60)
public class RestProxyTest {
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void createsAWorkingClientForAnAnnotatedService() {
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();
        HttpClient httpClient = request -> {
            capturedRequest.set(request);
            return Mono.just(new EmptyHttpResponse(request));
        };
        HttpPipeline pipeline = new HttpPipelineBuilder().httpClient(httpClient).build();
        WidgetService service = RestProxy.create(WidgetService.class, pipeline, new JacksonAdapter());

        Response<Void> response = service.getWidget().block(IO_TIMEOUT);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(204);
        assertThat(capturedRequest.get().getUrl().toString()).isEqualTo("https://example.test/widgets/7");
    }

    @Host("https://example.test")
    @ServiceInterface(name = "WidgetService")
    public interface WidgetService {
        @Get("widgets/7")
        @ExpectedResponses({204})
        Mono<Response<Void>> getWidget();
    }

    private static final class EmptyHttpResponse extends HttpResponse {
        private static final byte[] BODY = new byte[0];
        private final HttpHeaders headers = new HttpHeaders();

        private EmptyHttpResponse(HttpRequest request) {
            super(request);
        }

        @Override
        public int getStatusCode() {
            return 204;
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
            return Flux.just(ByteBuffer.wrap(BODY));
        }

        @Override
        public Mono<byte[]> getBodyAsByteArray() {
            return Mono.just(BODY.clone());
        }

        @Override
        public Mono<String> getBodyAsString() {
            return Mono.just(new String(BODY, UTF_8));
        }

        @Override
        public Mono<String> getBodyAsString(Charset charset) {
            return Mono.just(new String(BODY, charset));
        }
    }
}
