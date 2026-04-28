/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.lang.Thread.UncaughtExceptionHandler;

import io.netty.util.internal.chmv8.ForkJoinPool;
import io.netty.util.internal.chmv8.ForkJoinWorkerThread;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ForkJoinPoolTest implements ForkJoinPool.ForkJoinWorkerThreadFactory, UncaughtExceptionHandler {
    private static final String PARALLELISM_PROPERTY = "java.util.concurrent.ForkJoinPool.common.parallelism";
    private static final String THREAD_FACTORY_PROPERTY = "java.util.concurrent.ForkJoinPool.common.threadFactory";
    private static final String EXCEPTION_HANDLER_PROPERTY = "java.util.concurrent.ForkJoinPool.common.exceptionHandler";
    private static final String PREVIOUS_PARALLELISM = System.getProperty(PARALLELISM_PROPERTY);
    private static final String PREVIOUS_THREAD_FACTORY = System.getProperty(THREAD_FACTORY_PROPERTY);
    private static final String PREVIOUS_EXCEPTION_HANDLER = System.getProperty(EXCEPTION_HANDLER_PROPERTY);

    static {
        System.setProperty(PARALLELISM_PROPERTY, "1");
        System.setProperty(THREAD_FACTORY_PROPERTY, ForkJoinPoolTest.class.getName());
        System.setProperty(EXCEPTION_HANDLER_PROPERTY, ForkJoinPoolTest.class.getName());
    }

    @AfterAll
    static void restoreCommonPoolProperties() {
        restoreProperty(PARALLELISM_PROPERTY, PREVIOUS_PARALLELISM);
        restoreProperty(THREAD_FACTORY_PROPERTY, PREVIOUS_THREAD_FACTORY);
        restoreProperty(EXCEPTION_HANDLER_PROPERTY, PREVIOUS_EXCEPTION_HANDLER);
    }

    @Test
    void commonPoolLoadsConfiguredFactoryAndExceptionHandler() {
        ForkJoinPool pool = ForkJoinPool.commonPool();

        Assertions.assertEquals(1, pool.getParallelism());
        Assertions.assertSame(ForkJoinPoolTest.class, pool.getFactory().getClass());
        Assertions.assertSame(ForkJoinPoolTest.class, pool.getUncaughtExceptionHandler().getClass());
    }

    @Override
    public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
        return new TestWorker(pool);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable exception) {
        Assertions.fail("The common pool worker should not fail", exception);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    public static final class TestWorker extends ForkJoinWorkerThread {
        private TestWorker(ForkJoinPool pool) {
            super(pool);
        }
    }
}
