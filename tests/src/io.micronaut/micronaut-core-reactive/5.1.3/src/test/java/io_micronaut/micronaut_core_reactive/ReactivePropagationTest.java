/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_core_reactive;

import io.micronaut.core.async.propagation.ReactivePropagation;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ReactivePropagationTest {
    @Test
    void bindsContextForDeferredSignalsAndRestoresCallerContext() throws Exception {
        PropagatedContext context = PropagatedContext.empty().plus(new RequestContext("request-a"));
        CompletableFuture<String> source = new CompletableFuture<>();
        Publisher<String> wrapped = ReactivePropagation.propagate(context, Publishers.fromCompletableFuture(source));
        CompletableFuture<List<String>> result = Flux.from(wrapped)
                .map(value -> value + ":" + PropagatedContext.get().get(RequestContext.class).value())
                .collectList().toFuture();

        assertFalse(PropagatedContext.exists());
        source.complete("payload");
        assertEquals(List.of("payload:request-a"), result.get(10, TimeUnit.SECONDS));
        assertFalse(PropagatedContext.exists());
    }

    @Test
    void exposesPropagatedContextInReactorSubscriberContext() {
        RequestContext request = new RequestContext("reactor-request");
        PropagatedContext context = PropagatedContext.empty().plus(request);
        Mono<String> source = Mono.deferContextual(view -> Mono.just(
                ReactorPropagation.findContextElement(view, RequestContext.class).orElseThrow().value()));

        Publisher<String> wrapped = ReactivePropagation.propagate(context, source);
        assertEquals("reactor-request", Mono.from(wrapped).block(Duration.ofSeconds(10)));
        assertFalse(PropagatedContext.exists());
    }

    @Test
    void bindsContextForErrorSignals() {
        PropagatedContext context = PropagatedContext.empty().plus(new RequestContext("failed-request"));
        CompletableFuture<String> source = new CompletableFuture<>();
        AtomicReference<String> observed = new AtomicReference<>();
        CompletableFuture<List<String>> result = Flux.from(ReactivePropagation.propagate(
                context, Publishers.fromCompletableFuture(source)))
                .doOnError(error -> observed.set(PropagatedContext.get().get(RequestContext.class).value()))
                .collectList().toFuture();
        IllegalStateException failure = new IllegalStateException("request rejected");

        source.completeExceptionally(failure);
        ExecutionException thrown = assertThrows(ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
        assertSame(failure, thrown.getCause());
        assertEquals("failed-request", observed.get());
        assertFalse(PropagatedContext.exists());
    }

    private record RequestContext(String value) implements PropagatedContextElement {
    }
}
