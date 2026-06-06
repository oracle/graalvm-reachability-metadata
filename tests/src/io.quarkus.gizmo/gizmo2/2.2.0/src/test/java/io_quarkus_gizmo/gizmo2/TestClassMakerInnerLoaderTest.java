/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus_gizmo.gizmo2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import io.quarkus.gizmo2.testing.TestClassMaker;

public class TestClassMakerInnerLoaderTest {
    private static final String GENERATED_CLASS_NAME =
            "io_quarkus_gizmo.gizmo2.generated.TestClassMakerInnerLoaderTarget";
    private static final int GENERATED_METHOD_COUNT = 500;

    @Test
    void loadsSameGeneratedClassFromParallelLoaderRequests() throws Exception {
        try {
            exerciseParallelLocalClassLoading();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void exerciseParallelLocalClassLoading() throws Exception {
        TestClassMaker maker = TestClassMaker.create();
        maker.gizmo().class_(GENERATED_CLASS_NAME, classCreator -> {
            classCreator.defaultConstructor();
            for (int i = 0; i < GENERATED_METHOD_COUNT; i++) {
                String value = "value-" + i;
                classCreator.staticMethod("value" + i, methodCreator -> {
                    methodCreator.returning(String.class);
                    methodCreator.body(block -> block.return_(value));
                });
            }
        });

        int threadCount = Math.min(32, Math.max(8, Runtime.getRuntime().availableProcessors() * 4));
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            Set<Class<?>> loadedClasses = loadClassConcurrently(
                    maker.classLoader(), GENERATED_CLASS_NAME, threadCount, executor);
            assertThat(loadedClasses).containsExactly(maker.classLoader().loadClass(GENERATED_CLASS_NAME));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Set<Class<?>> loadClassConcurrently(ClassLoader classLoader, String className, int threadCount,
            ExecutorService executor) throws Exception {
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Class<?>>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return classLoader.loadClass(className);
            }));
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        Set<Class<?>> loadedClasses = new HashSet<>();
        for (Future<Class<?>> future : futures) {
            loadedClasses.add(getLoadedClass(future));
        }
        return loadedClasses;
    }

    private static Class<?> getLoadedClass(Future<Class<?>> future) throws Exception {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError(cause);
        } catch (TimeoutException e) {
            throw new AssertionError("Timed out while loading the generated class", e);
        }
    }
}
