/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import static org.assertj.core.api.Assertions.assertThat;

public class FluxRetryWhenInnerRetryWhenMainSubscriberTest {

    @Test
    void resubscribesUntilTheSourceSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        Flux<String> source = Flux.defer(() -> {
            if (attempts.incrementAndGet() < 3) {
                return Flux.error(new IllegalStateException("not ready"));
            }
            return Flux.just("ready", "done");
        });

        List<String> values = source.retryWhen(Retry.max(2))
                .collectList()
                .block(Duration.ofSeconds(10));

        assertThat(values).containsExactly("ready", "done");
        assertThat(attempts).hasValue(3);
    }
}
