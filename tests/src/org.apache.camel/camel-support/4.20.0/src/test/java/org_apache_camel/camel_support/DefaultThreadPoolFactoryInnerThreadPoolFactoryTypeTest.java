/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_support;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.camel.support.DefaultThreadPoolFactory;
import org.apache.camel.util.concurrent.ThreadFactoryTypeAware;
import org.apache.camel.util.concurrent.ThreadType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultThreadPoolFactoryInnerThreadPoolFactoryTypeTest {
    @BeforeAll
    static void enableVirtualThreadPools() {
        System.setProperty("camel.threads.virtual.enabled", "true");

        assertThat(ThreadType.current()).isEqualTo(ThreadType.VIRTUAL);
    }

    @Test
    void newCachedThreadPoolUsesThreadPerTaskExecutorForVirtualThreadFactory() throws Exception {
        DefaultThreadPoolFactory poolFactory = new DefaultThreadPoolFactory();
        ExecutorService executorService = poolFactory.newCachedThreadPool(new VirtualThreadFactory());

        try {
            Future<Boolean> task = executorService.submit(() -> Thread.currentThread().isVirtual());

            assertThat(task.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class VirtualThreadFactory implements ThreadFactoryTypeAware {
        private final ThreadFactory delegate = Thread.ofVirtual().name("camel-support-test-", 0).factory();

        @Override
        public Thread newThread(Runnable task) {
            return delegate.newThread(task);
        }

        @Override
        public boolean isVirtual() {
            return true;
        }
    }
}
