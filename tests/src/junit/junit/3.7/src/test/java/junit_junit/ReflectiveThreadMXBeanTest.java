/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.rules.Timeout;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestTimedOutException;

public class ReflectiveThreadMXBeanTest {

    @Test
    void timeoutStuckThreadDetectionReadsThreadCpuTime() throws Throwable {
        enableThreadCpuTimingIfAvailable();

        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch busyThreadStarted = new CountDownLatch(1);
        CountDownLatch keepTimedTestAlive = new CountDownLatch(1);
        AtomicReference<Thread> busyThread = new AtomicReference<>();

        Statement originalStatement = new Statement() {
            @Override
            public void evaluate() throws Exception {
                Thread worker = new Thread(Thread.currentThread().getThreadGroup(), () -> {
                    busyThreadStarted.countDown();
                    long accumulator = 0L;
                    while (running.get()) {
                        accumulator += System.nanoTime();
                        if ((accumulator & 0x3fff) == 0L) {
                            Thread.yield();
                        }
                    }
                }, "junit-cpu-bound-stuck-thread");
                worker.setDaemon(true);
                busyThread.set(worker);
                worker.start();

                assertThat(busyThreadStarted.await(5, TimeUnit.SECONDS)).isTrue();
                keepTimedTestAlive.await(5, TimeUnit.SECONDS);
            }
        };
        Statement timeoutStatement = Timeout.builder()
                .withTimeout(1, TimeUnit.SECONDS)
                .withLookingForStuckThread(true)
                .build()
                .apply(originalStatement, Description.createTestDescription(getClass(), "stuckThread"));

        try {
            Throwable thrown = catchThrowable(timeoutStatement::evaluate);

            assertThat(thrown).isNotNull();
            assertThat(isTimeoutFailure(thrown)).isTrue();
        } finally {
            running.set(false);
            keepTimedTestAlive.countDown();
            Thread worker = busyThread.get();
            if (worker != null) {
                worker.join(TimeUnit.SECONDS.toMillis(5));
            }
        }
    }

    private static void enableThreadCpuTimingIfAvailable() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        if (threadMXBean.isThreadCpuTimeSupported() && !threadMXBean.isThreadCpuTimeEnabled()) {
            threadMXBean.setThreadCpuTimeEnabled(true);
        }
    }

    private static boolean isTimeoutFailure(Throwable throwable) {
        if (throwable instanceof TestTimedOutException) {
            return true;
        }
        if (throwable instanceof MultipleFailureException) {
            MultipleFailureException failures = (MultipleFailureException) throwable;
            return failures.getFailures().stream().anyMatch(TestTimedOutException.class::isInstance);
        }
        return false;
    }
}
