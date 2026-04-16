/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.mchange.v2.async;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.mchange.v2.log.MLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class ThreadPoolAsynchronousRunnerTest {
    @Test
    void getStackTracesIncludesTheActiveWorkerThread() throws Exception {
        ThreadPoolAsynchronousRunner runner = newRunner("stack-trace-runner");
        BlockingTask blockingTask = new BlockingTask();

        runner.postRunnable(blockingTask);
        blockingTask.awaitStarted();

        try {
            String stackTraces = runner.getStackTraces();

            assertThat(stackTraces)
                    .isNotNull()
                    .contains("stack-trace-runner-#0");
        } finally {
            blockingTask.release();
            runner.close(true);
        }
    }

    @Test
    void deadlockDetectorLogsTheFullJvmThreadDumpWhenFinestLoggingIsEnabled() throws Exception {
        ThreadPoolAsynchronousRunner runner = newRunner("deadlock-runner");
        BlockingTask blockingTask = new BlockingTask();
        RecordingHandler recordingHandler = new RecordingHandler();
        Logger julLogger = Logger.getLogger(ThreadPoolAsynchronousRunner.class.getName());
        Level originalLevel = julLogger.getLevel();

        runner.postRunnable(blockingTask);
        blockingTask.awaitStarted();

        julLogger.addHandler(recordingHandler);
        julLogger.setLevel(Level.FINEST);

        try {
            runner.postRunnable(() -> {
            });

            runner.deadlockDetector.run();
            runner.deadlockDetector.run();

            assertThat(ThreadPoolAsynchronousRunner.logger.isLoggable(MLevel.FINEST)).isTrue();
            assertThat(runner.getPendingTaskCount()).isZero();
            assertThat(recordingHandler.messages())
                    .anySatisfy(message -> assertThat(message).contains("APPARENT DEADLOCK"))
                    .anySatisfy(message -> assertThat(message).contains("full JVM thread dump"));
        } finally {
            blockingTask.release();
            runner.close(true);
            julLogger.removeHandler(recordingHandler);
            julLogger.setLevel(originalLevel);
        }
    }

    private static ThreadPoolAsynchronousRunner newRunner(String threadLabel) {
        return new ThreadPoolAsynchronousRunner(1, true, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, threadLabel);
    }

    private static final class BlockingTask implements Runnable {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void run() {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        void awaitStarted() throws InterruptedException {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        }

        void release() {
            release.countDown();
        }
    }

    private static final class RecordingHandler extends Handler {
        private final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.FINEST.intValue()) {
                messages.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<String> messages() {
            return messages;
        }
    }
}
