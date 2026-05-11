/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.chmv8.ForkJoinPool;
import io.netty.util.internal.chmv8.ForkJoinWorkerThread;
import org.junit.jupiter.api.Test;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class ForkJoinPoolTest {
    private static final String COMMON_PARALLELISM_PROPERTY =
            "java.util.concurrent.ForkJoinPool.common.parallelism";
    private static final String COMMON_THREAD_FACTORY_PROPERTY =
            "java.util.concurrent.ForkJoinPool.common.threadFactory";
    private static final String COMMON_EXCEPTION_HANDLER_PROPERTY =
            "java.util.concurrent.ForkJoinPool.common.exceptionHandler";

    private static final AtomicInteger FACTORY_CONSTRUCTIONS = new AtomicInteger();
    private static final AtomicInteger HANDLER_CONSTRUCTIONS = new AtomicInteger();

    @Test
    void commonPoolLoadsConfiguredFactoryAndExceptionHandler() {
        String previousParallelism = System.getProperty(COMMON_PARALLELISM_PROPERTY);
        String previousFactory = System.getProperty(COMMON_THREAD_FACTORY_PROPERTY);
        String previousHandler = System.getProperty(COMMON_EXCEPTION_HANDLER_PROPERTY);
        try {
            System.setProperty(COMMON_PARALLELISM_PROPERTY, "0");
            System.setProperty(COMMON_THREAD_FACTORY_PROPERTY, ConfiguredWorkerThreadFactory.class.getName());
            System.setProperty(COMMON_EXCEPTION_HANDLER_PROPERTY, ConfiguredExceptionHandler.class.getName());

            ForkJoinPool pool = ForkJoinPool.commonPool();

            assertThat(pool.getFactory()).isInstanceOf(ConfiguredWorkerThreadFactory.class);
            assertThat(pool.getUncaughtExceptionHandler()).isInstanceOf(ConfiguredExceptionHandler.class);
            assertThat(FACTORY_CONSTRUCTIONS).hasValue(1);
            assertThat(HANDLER_CONSTRUCTIONS).hasValue(1);
        } finally {
            restoreProperty(COMMON_PARALLELISM_PROPERTY, previousParallelism);
            restoreProperty(COMMON_THREAD_FACTORY_PROPERTY, previousFactory);
            restoreProperty(COMMON_EXCEPTION_HANDLER_PROPERTY, previousHandler);
        }
    }

    private static void restoreProperty(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }

    public static final class ConfiguredWorkerThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
        public ConfiguredWorkerThreadFactory() {
            FACTORY_CONSTRUCTIONS.incrementAndGet();
        }

        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            return new ConfiguredWorkerThread(pool);
        }
    }

    public static final class ConfiguredExceptionHandler implements UncaughtExceptionHandler {
        public ConfiguredExceptionHandler() {
            HANDLER_CONSTRUCTIONS.incrementAndGet();
        }

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
        }
    }

    private static final class ConfiguredWorkerThread extends ForkJoinWorkerThread {
        private ConfiguredWorkerThread(ForkJoinPool pool) {
            super(pool);
        }
    }
}
