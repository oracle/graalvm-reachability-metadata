/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.util.CustomRejectionPolicy;
import org.jgroups.util.Util;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomRejectionPolicyTest {
    @BeforeAll
    static void configureLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    @Test
    void parsesAndDelegatesToCustomRejectedExecutionHandler() {
        CountingRejectedExecutionHandler.reset();
        RejectedExecutionHandler policy = Util.parseRejectionPolicy(
                CustomRejectionPolicy.NAME + "=" + CountingRejectedExecutionHandler.class.getName());
        RejectedTask task = new RejectedTask();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                10,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());

        try {
            policy.rejectedExecution(task, executor);

            assertThat(policy).isInstanceOf(CustomRejectionPolicy.class);
            assertThat(CountingRejectedExecutionHandler.instances).hasValue(1);
            assertThat(CountingRejectedExecutionHandler.rejections).hasValue(1);
            assertThat(CountingRejectedExecutionHandler.lastTask.get()).isSameAs(task);
            assertThat(CountingRejectedExecutionHandler.lastExecutor.get()).isSameAs(executor);
        } finally {
            executor.shutdownNow();
        }
    }

    public static class CountingRejectedExecutionHandler implements RejectedExecutionHandler {
        private static final AtomicInteger instances = new AtomicInteger();
        private static final AtomicInteger rejections = new AtomicInteger();
        private static final AtomicReference<Runnable> lastTask = new AtomicReference<>();
        private static final AtomicReference<ThreadPoolExecutor> lastExecutor = new AtomicReference<>();

        public CountingRejectedExecutionHandler() {
            instances.incrementAndGet();
        }

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            rejections.incrementAndGet();
            lastTask.set(task);
            lastExecutor.set(executor);
        }

        private static void reset() {
            instances.set(0);
            rejections.set(0);
            lastTask.set(null);
            lastExecutor.set(null);
        }
    }

    public static class RejectedTask implements Runnable {
        private static final AtomicInteger runs = new AtomicInteger();

        @Override
        public void run() {
            runs.incrementAndGet();
        }
    }
}
