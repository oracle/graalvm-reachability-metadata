/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_http_netty;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import io.micronaut.http.netty.channel.loom.PrivateLoomSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class PrivateLoomSupportTest {
    @Test
    @Timeout(55)
    void usesConfiguredExecutorToRunAVirtualThread() throws Exception {
        ExecutorService scheduler = Executors.newSingleThreadExecutor();
        try {
            assertThat(PrivateLoomSupport.isSupported()).isTrue();

            Thread.Builder.OfVirtual builder = Thread.ofVirtual().name("micronaut-loom-test");
            PrivateLoomSupport.setScheduler(builder, scheduler);
            FutureTask<SchedulerObservation> observationTask = new FutureTask<>(() -> new SchedulerObservation(
                    PrivateLoomSupport.getScheduler(Thread.currentThread()),
                    PrivateLoomSupport.getCarrierThread(Thread.currentThread())));
            Thread virtualThread = builder.unstarted(observationTask);

            virtualThread.start();
            SchedulerObservation observation = observationTask.get(10, TimeUnit.SECONDS);

            assertThat(virtualThread.join(Duration.ofSeconds(10))).isTrue();
            assertThat(virtualThread.isVirtual()).isTrue();
            assertThat(observation.scheduler()).isSameAs(scheduler);
            assertThat(observation.carrier()).isNotNull();
            assertThat(observation.carrier().isVirtual()).isFalse();
        } finally {
            scheduler.shutdownNow();
            scheduler.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private record SchedulerObservation(Executor scheduler, Thread carrier) {
    }
}
