/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestTimedOutException;

public class ReflectiveThreadMXBeanTest {
    @Test
    void timeoutRuleLooksForRunnableStuckThreadCpuTime() throws Throwable {
        AtomicBoolean keepWorkerRunning = new AtomicBoolean(true);
        AtomicReference<Thread> workerReference = new AtomicReference<>();
        CountDownLatch workerStarted = new CountDownLatch(1);

        Statement blockingStatement = new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Thread worker = new Thread(() -> {
                    workerStarted.countDown();
                    while (keepWorkerRunning.get()) {
                        Thread.onSpinWait();
                    }
                }, "junit-timeout-runnable-worker");
                worker.setDaemon(true);
                workerReference.set(worker);
                worker.start();

                try {
                    assertThat(workerStarted.await(1, SECONDS)).isTrue();
                    SECONDS.sleep(5L);
                } finally {
                    keepWorkerRunning.set(false);
                    worker.join(1_000L);
                }
            }
        };

        Statement timeoutStatement = Timeout.builder()
                .withTimeout(250L, MILLISECONDS)
                .withLookingForStuckThread(true)
                .build()
                .apply(
                        blockingStatement,
                        Description.createTestDescription(TimeoutDrivenJUnit4Test.class, "timesOut"));

        Throwable thrown = catchThrowable(timeoutStatement::evaluate);

        keepWorkerRunning.set(false);
        Thread worker = workerReference.get();
        if (worker != null) {
            worker.join(1_000L);
        }
        assertThat(thrown).isInstanceOfAny(TestTimedOutException.class, MultipleFailureException.class);
    }

    public static final class TimeoutDrivenJUnit4Test {
        @org.junit.Test
        public void timesOut() {
        }
    }
}
