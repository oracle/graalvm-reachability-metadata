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
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThat;

public class ParallelSchedulerTest {

    @Test
    void processesAllParallelRailsAndDisposesTheScheduler() {
        Scheduler scheduler = Schedulers.newParallel("parallel-rails-test", 2);
        try {
            List<Integer> values = Flux.range(1, 6)
                    .parallel(2)
                    .runOn(scheduler)
                    .map(value -> value * 10)
                    .sequential()
                    .collectList()
                    .block(Duration.ofSeconds(10));

            assertThat(values).containsExactlyInAnyOrder(10, 20, 30, 40, 50, 60);
        } finally {
            scheduler.dispose();
        }
        assertThat(scheduler.isDisposed()).isTrue();
    }
}
