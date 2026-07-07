/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.tomcat.util.compat.Jre19Compat;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jre19CompatTest {

    @Test
    void getExecutorFindsTomcatExecutorStoredInThreadHolderTask() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HolderThreadFactory threadFactory = new HolderThreadFactory();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                threadFactory);
        Future<?> submittedTask = executor.submit(new AwaitingTask(running, release));

        try {
            assertThat(running.await(10, TimeUnit.SECONDS)).isTrue();
            HolderThread workerThread = threadFactory.thread();

            assertThat(workerThread).isNotNull();
            assertThat(new Jre19Compat().getExecutor(workerThread)).isSameAs(executor);

            release.countDown();
            submittedTask.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class AwaitingTask implements Runnable {
        private final CountDownLatch running;
        private final CountDownLatch release;

        private AwaitingTask(CountDownLatch running, CountDownLatch release) {
            this.running = running;
            this.release = release;
        }

        @Override
        public void run() {
            running.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release executor task");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to release executor task", e);
            }
        }
    }

    private static final class HolderThreadFactory implements ThreadFactory {
        private volatile HolderThread thread;

        @Override
        public Thread newThread(Runnable runnable) {
            HolderThread result = new HolderThread(runnable);
            thread = result;
            return result;
        }

        private HolderThread thread() {
            return thread;
        }
    }

    public static final class HolderThread extends Thread {
        public final Holder holder;

        private HolderThread(Runnable task) {
            super("jre19-compat-holder-thread");
            this.holder = new Holder(task);
            setDaemon(true);
        }

        @Override
        public void run() {
            ((Runnable) holder.task).run();
        }
    }

    public static final class Holder {
        public final Object task;

        private Holder(Object task) {
            this.task = task;
        }
    }
}
