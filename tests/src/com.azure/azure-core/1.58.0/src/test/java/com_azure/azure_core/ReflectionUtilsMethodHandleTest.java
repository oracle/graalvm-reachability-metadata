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
import com.azure.core.http.rest.RestProxy;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.util.serializer.JacksonAdapter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Timeout(60)
public class ReflectionUtilsMethodHandleTest {
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void materializesACustomResponseThroughTheRestProxy() {
        HttpClient httpClient = request -> Mono.just(new JsonHttpResponse(request));
        HttpPipeline pipeline = new HttpPipelineBuilder().httpClient(httpClient).build();
        WidgetService service = RestProxy.create(WidgetService.class, pipeline, new JacksonAdapter());

        CustomResponse response = service.getWidget().block(IO_TIMEOUT);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getHeaders().getValue("etag")).isEqualTo("widget-7");
        assertThat(response.getValue()).isEqualTo("available");
    }

    @Host("https://example.test")
    @ServiceInterface(name = "WidgetService")
    public interface WidgetService {
        @Get("widgets/7")
        @ExpectedResponses({200})
        Mono<CustomResponse> getWidget();
    }

    public static final class CustomResponse extends SimpleResponse<String> {
        public CustomResponse(HttpRequest request, int statusCode, HttpHeaders headers, String value) {
            super(request, statusCode, headers, value);
        }
    }

    private static final class JsonHttpResponse extends HttpResponse {
        private static final byte[] BODY = "\"available\"".getBytes(UTF_8);
        private final HttpHeaders headers = new HttpHeaders()
                .set("content-type", "application/json")
                .set("etag", "widget-7");

        private JsonHttpResponse(HttpRequest request) {
            super(request);
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
