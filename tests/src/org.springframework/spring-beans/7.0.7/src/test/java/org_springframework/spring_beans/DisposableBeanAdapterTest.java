/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DisposableBeanAdapterTest {
    @Test
    void infersCloseAndShutdownMethods() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition closeDefinition = new RootBeanDefinition(CloseableResource.class);
        closeDefinition.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
        factory.registerBeanDefinition("close", closeDefinition);
        RootBeanDefinition shutdownDefinition = new RootBeanDefinition(ShutdownResource.class);
        shutdownDefinition.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
        factory.registerBeanDefinition("shutdown", shutdownDefinition);
        CloseableResource close = factory.getBean("close", CloseableResource.class);
        ShutdownResource shutdown = factory.getBean("shutdown", ShutdownResource.class);
        factory.destroySingletons();
        assertTrue(close.closed);
        assertTrue(shutdown.stopped);
    }

    @Test
    void invokesPublicCustomDestroyMethod() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(ShutdownResource.class);
        definition.setNonPublicAccessAllowed(false);
        definition.setDestroyMethodName("shutdown");
        factory.registerBeanDefinition("resource", definition);
        ShutdownResource resource = factory.getBean("resource", ShutdownResource.class);
        factory.destroySingletons();
        assertTrue(resource.stopped);
    }

    @Test
    void infersExecutorShutdownWithoutStartingThreads() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        try {
            DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
            RootBeanDefinition definition = new RootBeanDefinition(ScheduledThreadPoolExecutor.class, () -> executor);
            definition.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
            factory.registerBeanDefinition("executor", definition);
            factory.getBean("executor");
            factory.destroySingletons();
            assertTrue(executor.isShutdown());
        } finally {
            executor.shutdownNow();
        }
    }

    public static class CloseableResource {
        private boolean closed;

        public void close() {
            this.closed = true;
        }
    }

    public static class ShutdownResource {
        private boolean stopped;

        public void shutdown() {
            this.stopped = true;
        }
    }
}
