/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_reactor_3_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ContextPropagationOperatorTest {
    private static final ContextKey<String> VALUE_KEY = ContextKey.named("reactor-test-value");

    private ContextPropagationOperator operator;

    @AfterEach
    void resetOperator() {
        if (operator != null) {
            operator.resetOnEachOperator();
        }
    }

    @Test
    void propagatesContextThroughMonoAndFlux() {
        operator = ContextPropagationOperator.create();
        operator.registerOnEachOperator();

        Context tracingContext =
                Context.root()
                        .with(VALUE_KEY, "propagated")
                        .with(Span.wrap(validSpanContext()));

        String monoValue =
                ContextPropagationOperator.runWithContext(
                                Mono.defer(() -> Mono.just(Context.current().get(VALUE_KEY))),
                                tracingContext)
                        .block(Duration.ofSeconds(10));
        List<String> fluxValues =
                ContextPropagationOperator.runWithContext(
                                Flux.defer(() -> Flux.just(Context.current().get(VALUE_KEY))),
                                tracingContext)
                        .collectList()
                        .block(Duration.ofSeconds(10));

        assertThat(monoValue).isEqualTo("propagated");
        assertThat(fluxValues).containsExactly("propagated");
    }

    private static SpanContext validSpanContext() {
        return SpanContext.create(
                "00000000000000000000000000000001",
                "0000000000000001",
                TraceFlags.getSampled(),
                TraceState.getDefault());
    }
}
