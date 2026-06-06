/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.async.ThreadPoolAsynchronousRunner;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadPoolAsynchronousRunnerTest {
    private static final String THREAD_POOL_LOGGER_NAME = "com.mchange.v2.async.ThreadPoolAsynchronousRunner";
    private static final String FALLBACK_CUTOFF_PROPERTY = "com.mchange.v2.log.FallbackMLog.DEFAULT_CUTOFF_LEVEL";

    private static Logger rootLogger;
    private static Logger threadPoolLogger;
    private static Level previousRootLevel;
    private static Level previousThreadPoolLoggerLevel;
    private static Map<Handler, Level> previousHandlerLevels;
    private static String previousFallbackCutoffLevel;

    @BeforeAll
    static void enableFinestLogging() {
        previousFallbackCutoffLevel = System.getProperty(FALLBACK_CUTOFF_PROPERTY);
        System.setProperty(FALLBACK_CUTOFF_PROPERTY, "FINEST");
        MLog.refreshConfig(null, null);

        rootLogger = LogManager.getLogManager().getLogger("");
        threadPoolLogger = Logger.getLogger(THREAD_POOL_LOGGER_NAME);
        previousRootLevel = rootLogger.getLevel();
        previousThreadPoolLoggerLevel = threadPoolLogger.getLevel();
        previousHandlerLevels = new IdentityHashMap<>();
        for (Handler handler : rootLogger.getHandlers()) {
            previousHandlerLevels.put(handler, handler.getLevel());
            handler.setLevel(Level.FINEST);
        }
        rootLogger.setLevel(Level.FINEST);
        threadPoolLogger.setLevel(Level.FINEST);
    }

    @AfterAll
    static void restoreLogging() {
        if (rootLogger != null) {
            rootLogger.setLevel(previousRootLevel);
        }
        if (threadPoolLogger != null) {
            threadPoolLogger.setLevel(previousThreadPoolLoggerLevel);
        }
        if (previousHandlerLevels != null) {
            for (Map.Entry<Handler, Level> entry : previousHandlerLevels.entrySet()) {
                entry.getKey().setLevel(entry.getValue());
            }
        }

        if (previousFallbackCutoffLevel == null) {
            System.clearProperty(FALLBACK_CUTOFF_PROPERTY);
        } else {
            System.setProperty(FALLBACK_CUTOFF_PROPERTY, previousFallbackCutoffLevel);
        }
        MLog.refreshConfig(null, null);
    }

    @Test
    void getStackTracesReturnsManagedWorkerThreadDump() throws InterruptedException {
        Timer timer = new Timer(true);
        ThreadPoolAsynchronousRunner runner = new ThreadPoolAsynchronousRunner(1, true, 0, 1000, 1000, timer, "stack-trace-runner");
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);

        try {
            runner.postRunnable(() -> {
                taskStarted.countDown();
                awaitLatch(releaseTask);
            });

            assertThat(taskStarted.await(5, TimeUnit.SECONDS)).isTrue();

            String stackTraces = runner.getStackTraces();

            assertThat(stackTraces)
                    .isNotBlank()
                    .contains("stack-trace-runner-#0")
                    .contains("java.util.concurrent.CountDownLatch");
        } finally {
            releaseTask.countDown();
            runner.close(true);
            timer.cancel();
        }
    }

    @Test
    void pendingTasksTriggerDeadlockDetectionAndRecovery() throws InterruptedException {
        assertThat(MLog.getLogger(ThreadPoolAsynchronousRunner.class).isLoggable(MLevel.FINEST)).isTrue();

        Timer timer = new Timer(true);
        ThreadPoolAsynchronousRunner runner = new ThreadPoolAsynchronousRunner(1, true, 0, 25, 25, timer, "deadlock-runner");
        CountDownLatch blockingTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseBlockingTask = new CountDownLatch(1);
        CountDownLatch pendingTaskExecuted = new CountDownLatch(1);

        try {
            runner.postRunnable(() -> {
                blockingTaskStarted.countDown();
                awaitLatch(releaseBlockingTask);
            });
            assertThat(blockingTaskStarted.await(5, TimeUnit.SECONDS)).isTrue();

            runner.postRunnable(pendingTaskExecuted::countDown);

            assertThat(pendingTaskExecuted.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseBlockingTask.countDown();
            runner.close(true);
            timer.cancel();
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
