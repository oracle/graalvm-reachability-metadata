/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import io.quarkus.bootstrap.forkjoin.QuarkusForkJoinWorkerThread;
import io.quarkus.bootstrap.forkjoin.QuarkusForkJoinWorkerThreadFactory;
import org.junit.jupiter.api.Test;

public class QuarkusForkJoinWorkerThreadFactoryTest {
    @Test
    void workersUseConfiguredApplicationClassLoader() throws Exception {
        try (URLClassLoader applicationClassLoader = new URLClassLoader(new URL[0], null)) {
            ForkJoinPool pool = new ForkJoinPool(1, new QuarkusForkJoinWorkerThreadFactory(), null, false);
            boolean terminated = false;

            QuarkusForkJoinWorkerThread.setQuarkusAppClassloader(applicationClassLoader);
            try {
                ClassLoader contextClassLoader = pool.submit(() -> Thread.currentThread().getContextClassLoader())
                        .get(5, TimeUnit.SECONDS);
                String workerClassName = pool.submit(() -> Thread.currentThread().getClass().getName())
                        .get(5, TimeUnit.SECONDS);

                assertThat(contextClassLoader).isSameAs(applicationClassLoader);
                assertThat(workerClassName).isEqualTo(QuarkusForkJoinWorkerThread.class.getName());
            } finally {
                pool.shutdownNow();
                terminated = pool.awaitTermination(5, TimeUnit.SECONDS);
                QuarkusForkJoinWorkerThread.setQuarkusAppClassloader(null);
            }

            assertThat(terminated).isTrue();
        }
    }
}
