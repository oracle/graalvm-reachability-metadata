/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Verifies Spring's public adapters preserve values across asynchronous APIs. */
public class ReactiveAdapterRegistryTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void adaptsCompletionStagesInBothDirectionsAndSupportsEmptyValues() throws Exception {
        ReactiveAdapterRegistry registry = new ReactiveAdapterRegistry();
        ReactiveAdapter adapter = registry.getAdapter(CompletableFuture.class);

        assertThat(adapter).isNotNull();
        assertThat(adapter.getDescriptor().isDeferred()).isFalse();
        assertThat(Mono.from(adapter.<String>toPublisher(CompletableFuture.completedFuture("ready"))).block(TIMEOUT))
                .isEqualTo("ready");

        CompletionStage<?> result = (CompletionStage<?>) adapter.fromPublisher(Mono.just("converted"));

        assertThat(result.toCompletableFuture().get(10, TimeUnit.SECONDS)).isEqualTo("converted");
        assertThat(Flux.from(adapter.toPublisher(null)).collectList().block(TIMEOUT)).isEmpty();
    }

    @Test
    void selectsAdaptersFromConcreteSourcesAndPreservesReactorCardinality() {
        ReactiveAdapterRegistry registry = new ReactiveAdapterRegistry();
        Mono<String> single = Mono.just("single");
        ReactiveAdapter singleAdapter = registry.getAdapter(null, Optional.of(single));
        ReactiveAdapter multiAdapter = registry.getAdapter(Flux.class);

        assertThat(singleAdapter).isNotNull();
        assertThat(singleAdapter.isMultiValue()).isFalse();
        assertThat(Mono.from(singleAdapter.<String>toPublisher(single)).block(TIMEOUT)).isEqualTo("single");
        assertThat(multiAdapter).isNotNull();
        assertThat(multiAdapter.isMultiValue()).isTrue();

        Object result = multiAdapter.fromPublisher(Flux.just("first", "second"));

        assertThat(result).isInstanceOf(Flux.class);
        assertThat(Flux.from(multiAdapter.<String>toPublisher(result)).collectList().block(TIMEOUT))
                .containsExactly("first", "second");
    }

    @Test
    void bridgesJdkFlowPublishersInBothDirections() {
        ReactiveAdapterRegistry registry = new ReactiveAdapterRegistry();
        ReactiveAdapter adapter = registry.getAdapter(Flow.Publisher.class);
        Flow.Publisher<String> source = JdkFlowAdapter.publisherToFlowPublisher(Flux.just("in", "order"));

        assertThat(adapter).isNotNull();
        assertThat(Flux.from(adapter.<String>toPublisher(source)).collectList().block(TIMEOUT))
                .containsExactly("in", "order");

        Object result = adapter.fromPublisher(Flux.just("out", "order"));

        assertThat(result).isInstanceOf(Flow.Publisher.class);
        Flow.Publisher<?> flowPublisher = (Flow.Publisher<?>) result;
        assertThat(JdkFlowAdapter.flowPublisherToFlux(flowPublisher).collectList().block(TIMEOUT))
                .isEqualTo(List.of("out", "order"));
    }
}
