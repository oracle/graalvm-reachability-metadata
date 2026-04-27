/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.AbstractExecutorService;
import edu.emory.mathcs.backport.java.util.concurrent.Callable;
import edu.emory.mathcs.backport.java.util.concurrent.ExecutionException;
import edu.emory.mathcs.backport.java.util.concurrent.Future;
import edu.emory.mathcs.backport.java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractExecutorServiceTest {
    @Test
    void submitWrapsRunnableAndCallableTasks() throws Exception {
        DirectExecutorService executor = new DirectExecutorService();
        boolean[] runnableCalled = new boolean[] {false};

        Future runnableFuture = executor.submit(new Runnable() {
            @Override
            public void run() {
                runnableCalled[0] = true;
            }
        }, "runnable-result");
        Future callableFuture = executor.submit(new Callable() {
            @Override
            public Object call() {
                return "callable-result";
            }
        });

        assertThat(runnableCalled[0]).isTrue();
        assertThat(runnableFuture.isDone()).isTrue();
        assertThat(runnableFuture.get()).isEqualTo("runnable-result");
        assertThat(callableFuture.isDone()).isTrue();
        assertThat(callableFuture.get()).isEqualTo("callable-result");
    }

    @Test
    void invokeAnyContinuesUntilTaskSucceeds() throws Exception {
        DirectExecutorService executor = new DirectExecutorService();
        List tasks = Arrays.asList(
                new Callable() {
                    @Override
                    public Object call() {
                        throw new IllegalStateException("first task fails");
                    }
                },
                new Callable() {
                    @Override
                    public Object call() {
                        return "successful-result";
                    }
                });

        Object result = executor.invokeAny(tasks);

        assertThat(result).isEqualTo("successful-result");
    }

    @Test
    void timedInvokeAnyReturnsCompletedTaskResult() throws Exception {
        DirectExecutorService executor = new DirectExecutorService();
        List tasks = Collections.singletonList(new Callable() {
            @Override
            public Object call() {
                return "timed-result";
            }
        });

        Object result = executor.invokeAny(tasks, 1, TimeUnit.SECONDS);

        assertThat(result).isEqualTo("timed-result");
    }

    @Test
    void invokeAllReturnsCompletedFuturesInTaskOrder() throws Exception {
        DirectExecutorService executor = new DirectExecutorService();
        List tasks = Arrays.asList(
                new Callable() {
                    @Override
                    public Object call() {
                        return "first";
                    }
                },
                new Callable() {
                    @Override
                    public Object call() {
                        return "second";
                    }
                });

        List futures = executor.invokeAll(tasks);

        assertThat(futures).hasSize(2);
        assertThat(((Future) futures.get(0)).get()).isEqualTo("first");
        assertThat(((Future) futures.get(1)).get()).isEqualTo("second");
    }

    @Test
    void timedInvokeAllReturnsCompletedFuturesInTaskOrder() throws Exception {
        DirectExecutorService executor = new DirectExecutorService();
        List tasks = Arrays.asList(
                new Callable() {
                    @Override
                    public Object call() {
                        return "first-timed";
                    }
                },
                new Callable() {
                    @Override
                    public Object call() {
                        return "second-timed";
                    }
                });

        List futures = executor.invokeAll(tasks, 1, TimeUnit.SECONDS);

        assertThat(futures).hasSize(2);
        assertThat(((Future) futures.get(0)).get()).isEqualTo("first-timed");
        assertThat(((Future) futures.get(1)).get()).isEqualTo("second-timed");
    }

    @Test
    void failedCallableResultIsExposedThroughReturnedFuture() {
        DirectExecutorService executor = new DirectExecutorService();
        Future failedFuture = executor.submit(new Callable() {
            @Override
            public Object call() {
                throw new IllegalArgumentException("boom");
            }
        });

        assertThatThrownBy(failedFuture::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    private static final class DirectExecutorService extends AbstractExecutorService {
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
            command.run();
        }
    }
}
