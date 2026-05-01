/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.AbstractExecutorService;
import edu.emory.mathcs.backport.java.util.concurrent.Callable;
import edu.emory.mathcs.backport.java.util.concurrent.Future;
import edu.emory.mathcs.backport.java.util.concurrent.RejectedExecutionException;
import edu.emory.mathcs.backport.java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class AbstractExecutorServiceTest {
    @Test
    void submitWrapsRunnableAndCallableTasksInFutures() throws Exception {
        DirectExecutorService executorService = new DirectExecutorService();
        List<String> events = new ArrayList<String>();

        Future runnableFuture = executorService.submit(new Runnable() {
            @Override
            public void run() {
                events.add("runnable");
            }
        }, "runnable-result");
        Future callableFuture = executorService.submit(new Callable() {
            @Override
            public Object call() {
                events.add("callable");
                return "callable-result";
            }
        });

        assertThat(events).containsExactly("runnable", "callable");
        assertThat(runnableFuture.isDone()).isTrue();
        assertThat(runnableFuture.get()).isEqualTo("runnable-result");
        assertThat(callableFuture.isDone()).isTrue();
        assertThat(callableFuture.get()).isEqualTo("callable-result");
    }

    @Test
    void bulkExecutionUsesAbstractExecutorServiceTaskManagement() throws Exception {
        DirectExecutorService executorService = new DirectExecutorService();
        List<Callable> tasks = Arrays.asList(new Callable[] {
                new ReturningCallable("alpha"),
                new ReturningCallable("bravo"),
                new ReturningCallable("charlie")
        });

        List futures = executorService.invokeAll(tasks);
        Object firstSuccessfulResult = executorService.invokeAny(tasks);

        assertThat(futures).hasSize(3);
        assertThat(((Future) futures.get(0)).get()).isEqualTo("alpha");
        assertThat(((Future) futures.get(1)).get()).isEqualTo("bravo");
        assertThat(((Future) futures.get(2)).get()).isEqualTo("charlie");
        assertThat(firstSuccessfulResult).isEqualTo("alpha");
        assertThat(executorService.executedTasks()).hasSize(4);
    }

    @Test
    void shutdownRejectsNewTasks() {
        DirectExecutorService executorService = new DirectExecutorService();

        executorService.shutdown();

        assertThat(executorService.isShutdown()).isTrue();
        assertThat(executorService.isTerminated()).isTrue();
        assertThatExceptionOfType(RejectedExecutionException.class)
                .isThrownBy(new ThrowingCallable() {
                    @Override
                    public void call() {
                        executorService.submit(new ReturningCallable("rejected"));
                    }
                });
    }

    private static final class ReturningCallable implements Callable {
        private final Object value;

        private ReturningCallable(Object value) {
            this.value = value;
        }

        @Override
        public Object call() {
            return value;
        }
    }

    private static final class DirectExecutorService extends AbstractExecutorService {
        private final List<Runnable> executedTasks = new ArrayList<Runnable>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new RejectedExecutionException("executor has been shut down");
            }
            executedTasks.add(command);
            command.run();
        }

        private List<Runnable> executedTasks() {
            return executedTasks;
        }
    }
}
