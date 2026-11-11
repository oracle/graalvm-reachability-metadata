/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThat;

public class SchedulerTaskTest {

    @Test
    void executesAScheduledRunnableAndAllowsDisposal() throws Exception {
        Scheduler scheduler = Schedulers.newSingle("scheduled-runnable-test");
        try {
            CompletableFuture<String> result = new CompletableFuture<>();
            Disposable task = scheduler.schedule(() -> result.complete("executed"));
            try {
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("executed");
                task.dispose();
                assertThat(task.isDisposed()).isTrue();
            } finally {
                task.dispose();
            }
        } finally {
            scheduler.dispose();
        }
    }
}
