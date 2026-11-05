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

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Verifies Mutiny adapters are discovered and used through Spring's public registry. */
public class ReactiveAdapterRegistryInnerMutinyRegistrarTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void convertsUniValuesInBothDirectionsAndSuppliesAnEmptyUni() {
        ReactiveAdapterRegistry registry = new ReactiveAdapterRegistry();
        ReactiveAdapter adapter = registry.getAdapter(Uni.class);

        assertThat(adapter).isNotNull();
        assertThat(adapter.isMultiValue()).isFalse();
        assertThat(Mono.from(adapter.<String>toPublisher(Uni.createFrom().item("from-uni"))).block(TIMEOUT))
                .isEqualTo("from-uni");

        Object result = adapter.fromPublisher(Mono.just("to-uni"));

        assertThat(result).isInstanceOf(Uni.class);
        assertThat(((Uni<?>) result).await().atMost(TIMEOUT)).isEqualTo("to-uni");
        assertThat(Flux.from(adapter.toPublisher(null)).collectList().block(TIMEOUT)).isEmpty();
    }

    @Test
    void convertsMultiValuesInBothDirectionsWithoutLosingOrder() {
        ReactiveAdapterRegistry registry = new ReactiveAdapterRegistry();
        ReactiveAdapter adapter = registry.getAdapter(Multi.class);

        assertThat(adapter).isNotNull();
        assertThat(adapter.isMultiValue()).isTrue();
        assertThat(Flux.from(adapter.<String>toPublisher(Multi.createFrom().items("a", "b")))
                .collectList().block(TIMEOUT)).containsExactly("a", "b");

        Object result = adapter.fromPublisher(Flux.just("c", "d"));

        assertThat(result).isInstanceOf(Multi.class);
        assertThat(((Multi<?>) result).collect().asList().await().atMost(TIMEOUT)).isEqualTo(List.of("c", "d"));
        assertThat(Flux.from(adapter.toPublisher(null)).collectList().block(TIMEOUT)).isEmpty();
    }
}
