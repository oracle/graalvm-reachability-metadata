/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import static org.assertj.core.api.Assertions.assertThat;

public class OperatorsInnerDeferredSubscriptionTest {

    @Test
    void defersTheSourceSubscriptionUntilTheGateCompletes() throws Exception {
        AtomicInteger subscriptions = new AtomicInteger();
        Sinks.Empty<Void> gate = Sinks.empty();
        Flux<Integer> source = Flux.defer(() -> {
            subscriptions.incrementAndGet();
            return Flux.just(10, 20);
        });
        CompletableFuture<List<Integer>> result = source.delaySubscription(gate.asMono())
                .collectList()
                .toFuture();
        try {
            assertThat(subscriptions).hasValue(0);
            assertThat(result).isNotDone();

            assertThat(gate.tryEmitEmpty()).isEqualTo(Sinks.EmitResult.OK);

            assertThat(result.get(10, TimeUnit.SECONDS)).containsExactly(10, 20);
            assertThat(subscriptions).hasValue(1);
        } finally {
            result.cancel(true);
        }
    }
}
