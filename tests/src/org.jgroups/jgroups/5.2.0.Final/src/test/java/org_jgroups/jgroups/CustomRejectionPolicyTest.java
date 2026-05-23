/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jgroups.util.CustomRejectionPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomRejectionPolicyTest {
    @Test
    void instantiatesAndDelegatesToConfiguredRejectedExecutionHandler() {
        RecordingRejectedExecutionHandler.reset();
        CustomRejectionPolicy policy = new CustomRejectionPolicy(
                CustomRejectionPolicy.NAME + "=" + RecordingRejectedExecutionHandler.class.getName());
        Runnable task = () -> { };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

        try {
            policy.rejectedExecution(task, executor);

            assertThat(RecordingRejectedExecutionHandler.constructorCalls()).hasValue(1);
            assertThat(RecordingRejectedExecutionHandler.rejectedTask()).hasValue(task);
            assertThat(RecordingRejectedExecutionHandler.rejectedExecutor()).hasValue(executor);
        } finally {
            executor.shutdownNow();
        }
    }

    public static class RecordingRejectedExecutionHandler implements RejectedExecutionHandler {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();
        private static final AtomicReference<Runnable> REJECTED_TASK = new AtomicReference<>();
        private static final AtomicReference<ThreadPoolExecutor> REJECTED_EXECUTOR = new AtomicReference<>();

        public RecordingRejectedExecutionHandler() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        static void reset() {
            CONSTRUCTOR_CALLS.set(0);
            REJECTED_TASK.set(null);
            REJECTED_EXECUTOR.set(null);
        }

        static AtomicInteger constructorCalls() {
            return CONSTRUCTOR_CALLS;
        }

        static AtomicReference<Runnable> rejectedTask() {
            return REJECTED_TASK;
        }

        static AtomicReference<ThreadPoolExecutor> rejectedExecutor() {
            return REJECTED_EXECUTOR;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            REJECTED_TASK.set(r);
            REJECTED_EXECUTOR.set(executor);
        }
    }
}
