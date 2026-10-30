/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_core_reactive;

import io.micronaut.core.async.publisher.DelayedSubscriber;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DelayedSubscriberTest {
    @Test
    void storesDownstreamDemandUntilUpstreamArrives() throws Exception {
        DelayedSubscriber<String> bridge = new DelayedSubscriber<>();
        CompletableFuture<List<String>> result = Flux.from(bridge).collectList().toFuture();
        assertFalse(result.isDone());

        Flux.just("north", "south").subscribe(bridge);
        assertEquals(List.of("north", "south"), result.get(10, TimeUnit.SECONDS));
    }

    @Test
    void connectsUpstreamBeforeDownstream() {
        DelayedSubscriber<Integer> bridge = new DelayedSubscriber<>();
        Flux.just(3, 5).subscribe(bridge);

        assertEquals(List.of(3, 5), Flux.from(bridge).collectList().block(Duration.ofSeconds(10)));
    }

    @Test
    void holdsEarlyCompletionUntilDownstreamSubscribes() {
        DelayedSubscriber<String> bridge = new DelayedSubscriber<>();
        Flux.<String>empty().subscribe(bridge);

        assertEquals(List.of(), Flux.from(bridge).collectList().block(Duration.ofSeconds(10)));
    }
}
