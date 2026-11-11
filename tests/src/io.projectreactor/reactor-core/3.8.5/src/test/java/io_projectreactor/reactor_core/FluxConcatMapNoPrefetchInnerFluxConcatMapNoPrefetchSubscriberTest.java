/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

public class FluxConcatMapNoPrefetchInnerFluxConcatMapNoPrefetchSubscriberTest {

    @Test
    void concatenatesInnerPublishersWithoutPrefetching() {
        List<Integer> subscriptions = new ArrayList<>();
        List<Integer> values = Flux.range(1, 3)
                .concatMap(value -> Flux.defer(() -> {
                    subscriptions.add(value);
                    return Flux.just(value, -value);
                }), 0)
                .collectList()
                .block(Duration.ofSeconds(10));

        assertThat(values).containsExactly(1, -1, 2, -2, 3, -3);
        assertThat(subscriptions).containsExactly(1, 2, 3);
    }
}
