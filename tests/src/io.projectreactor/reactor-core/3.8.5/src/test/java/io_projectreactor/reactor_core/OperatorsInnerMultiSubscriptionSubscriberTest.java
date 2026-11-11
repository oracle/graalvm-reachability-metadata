/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

public class OperatorsInnerMultiSubscriptionSubscriberTest {

    @Test
    void transfersDemandToTheFallbackWhenTheSourceIsEmpty() {
        List<String> values = Flux.<String>empty()
                .switchIfEmpty(Flux.just("fallback", "value"))
                .limitRate(1)
                .collectList()
                .block(Duration.ofSeconds(10));

        assertThat(values).containsExactly("fallback", "value");
    }
}
