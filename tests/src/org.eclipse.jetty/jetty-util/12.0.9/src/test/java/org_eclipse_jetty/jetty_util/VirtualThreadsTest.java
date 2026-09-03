/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.util.VirtualThreads;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VirtualThreadsTest {
    @Test
    void createsAndIdentifiesNamedVirtualThreads() throws Exception {
        assertThat(VirtualThreads.areSupported()).isTrue();

        Executor namedExecutor = VirtualThreads.getNamedVirtualThreadsExecutor("jetty-worker-");
        assertThat(namedExecutor).isInstanceOf(ExecutorService.class);
        ExecutorService executorService = (ExecutorService) namedExecutor;
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean virtual = new AtomicBoolean();
        AtomicReference<String> threadName = new AtomicReference<>();
        try {
            executorService.execute(new RecordingTask(completed, virtual, threadName));

            assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(virtual.get()).isTrue();
            assertThat(threadName.get()).startsWith("jetty-worker-");
        } finally {
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class RecordingTask implements Runnable {
        private final CountDownLatch completed;
        private final AtomicBoolean virtual;
        private final AtomicReference<String> threadName;

        private RecordingTask(
                CountDownLatch completed, AtomicBoolean virtual, AtomicReference<String> threadName) {
            this.completed = completed;
            this.virtual = virtual;
            this.threadName = threadName;
        }

        @Override
        public void run() {
            virtual.set(VirtualThreads.isVirtualThread());
            threadName.set(Thread.currentThread().getName());
            completed.countDown();
        }
    }
}
