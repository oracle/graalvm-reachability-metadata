/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThat;

public class MonoDelayInnerMonoDelayRunnableTest {

    @Test
    void emitsTheTimerValueOnTheProvidedScheduler() {
        Scheduler timer = Schedulers.newSingle("mono-delay-test");
        try {
            Long value = Mono.delay(Duration.ofMillis(1), timer)
                    .block(Duration.ofSeconds(10));

            assertThat(value).isEqualTo(0L);
        } finally {
            timer.dispose();
        }
    }
}
