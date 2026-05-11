/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jsr166_mirror.jsr166y;

import java.util.concurrent.TimeUnit;

import jsr166y.ForkJoinPool;
import jsr166y.ForkJoinTask;
import jsr166y.ForkJoinWorkerThread;
import jsr166y.RecursiveTask;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ForkJoinWorkerThreadAnonymous1Test {
    @Test
    void createsWorkerThreadAndRunsSubmittedTask() throws Exception {
        ForkJoinPool pool = new ForkJoinPool(1);
        try {
            ForkJoinTask<ThreadDetails> task = pool.submit(new WorkerThreadDetailsTask());

            ThreadDetails details = task.get(10, TimeUnit.SECONDS);

            assertThat(details.workerThread).isTrue();
            assertThat(details.threadName).contains("ForkJoinPool");
            assertThat(pool.getPoolSize()).isGreaterThanOrEqualTo(1);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class WorkerThreadDetailsTask extends RecursiveTask<ThreadDetails> {
        private static final long serialVersionUID = 1L;

        @Override
        protected ThreadDetails compute() {
            Thread thread = Thread.currentThread();
            return new ThreadDetails(thread instanceof ForkJoinWorkerThread, thread.getName());
        }
    }

    private static final class ThreadDetails {
        private final boolean workerThread;
        private final String threadName;

        private ThreadDetails(boolean workerThread, String threadName) {
            this.workerThread = workerThread;
            this.threadName = threadName;
        }
    }
}
