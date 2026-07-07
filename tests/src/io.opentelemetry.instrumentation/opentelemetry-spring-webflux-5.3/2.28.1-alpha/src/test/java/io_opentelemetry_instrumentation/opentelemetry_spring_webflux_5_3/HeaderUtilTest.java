/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_spring_webflux_5_3;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxClientTelemetry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.URI;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

public class HeaderUtilTest {
    @Test
    void clientTelemetryCapturesRequestHeaders() {
        AtomicBoolean telemetryStarted = new AtomicBoolean(false);
        List<ExchangeFilterFunction> filters = new ArrayList<>();

        SpringWebfluxClientTelemetry.builder(OpenTelemetry.noop())
                .setCapturedRequestHeaders(List.of("X-Test-Header"))
                .addAttributesExtractor(new VerifyingAttributesExtractor(telemetryStarted))
                .build()
                .addFilter(filters);

        assertThat(filters).hasSize(1);
        ClientRequest request = ClientRequest.create(
                        HttpMethod.GET, URI.create("https://example.test/hello"))
                .header("X-Test-Header", "covered")
                .build();

        HttpStatus status = filters.get(0)
                .filter(request, new OkExchangeFunction())
                .map(new StatusCodeFunction())
                .block(Duration.ofSeconds(10));

        assertThat(status).isEqualTo(HttpStatus.OK);
        assertThat(telemetryStarted.get()).isTrue();
    }

    private static final class OkExchangeFunction implements ExchangeFunction {
        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        }
    }

    private static final class StatusCodeFunction
            implements Function<ClientResponse, HttpStatus> {
        @Override
        public HttpStatus apply(ClientResponse response) {
            return response.statusCode();
        }
    }

    private static final class VerifyingAttributesExtractor
            implements AttributesExtractor<ClientRequest, ClientResponse> {
        private final AtomicBoolean telemetryStarted;

        private VerifyingAttributesExtractor(AtomicBoolean telemetryStarted) {
            this.telemetryStarted = telemetryStarted;
        }

        @Override
        public void onStart(
                AttributesBuilder attributes,
                Context parentContext,
                ClientRequest request) {
            telemetryStarted.set(true);
            assertThat(request.headers().getFirst("X-Test-Header")).isEqualTo("covered");
        }

        @Override
        public void onEnd(
                AttributesBuilder attributes,
                Context context,
                ClientRequest request,
                ClientResponse response,
                Throwable error) {
            assertThat(response).isNotNull();
            assertThat(error).isNull();
        }
    }
}
