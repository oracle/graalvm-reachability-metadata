/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import scala.concurrent.forkjoin.ForkJoinPool;
import scala.concurrent.forkjoin.ForkJoinWorkerThread;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises common-pool configuration through system properties. \u00A7FS-repository-functional-spec.5.2 */
public class ForkJoinPoolTest {
    private static final String EXCEPTION_HANDLER_PROPERTY =
            "java.util.concurrent.ForkJoinPool.common.exceptionHandler";
    private static final String THREAD_FACTORY_PROPERTY =
            "java.util.concurrent.ForkJoinPool.common.threadFactory";

    @Test
    void configuresTheCommonPoolFactoryAndExceptionHandler() {
        String previousFactory = System.setProperty(THREAD_FACTORY_PROPERTY, ConfiguredWorkerFactory.class.getName());
        String previousHandler =
                System.setProperty(EXCEPTION_HANDLER_PROPERTY, ConfiguredExceptionHandler.class.getName());

        try {
            ForkJoinPool commonPool = ForkJoinPool.commonPool();

            assertThat(commonPool.getFactory()).isExactlyInstanceOf(ConfiguredWorkerFactory.class);
            assertThat(commonPool.getUncaughtExceptionHandler())
                    .isExactlyInstanceOf(ConfiguredExceptionHandler.class);
        } finally {
            restoreProperty(THREAD_FACTORY_PROPERTY, previousFactory);
            restoreProperty(EXCEPTION_HANDLER_PROPERTY, previousHandler);
        }
    }

    private static void restoreProperty(String property, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, previousValue);
        }
    }

    public static final class ConfiguredWorkerFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            return new ConfiguredWorkerThread(pool);
        }
    }

    public static final class ConfiguredWorkerThread extends ForkJoinWorkerThread {
        public ConfiguredWorkerThread(ForkJoinPool pool) {
            super(pool);
        }
    }

    public static final class ConfiguredExceptionHandler implements Thread.UncaughtExceptionHandler {
        private final AtomicReference<Throwable> lastFailure = new AtomicReference<>();

        @Override
        public void uncaughtException(Thread thread, Throwable failure) {
            lastFailure.set(failure);
        }
    }
}
