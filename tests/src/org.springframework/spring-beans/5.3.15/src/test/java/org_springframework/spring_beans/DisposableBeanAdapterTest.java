/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class DisposableBeanAdapterTest {
    @Test
    void infersAndInvokesCloseAndShutdownMethods() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition closeDefinition = new RootBeanDefinition(CloseBean.class);
        closeDefinition.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
        factory.registerBeanDefinition("closeBean", closeDefinition);
        RootBeanDefinition shutdownDefinition = new RootBeanDefinition(ShutdownBean.class);
        shutdownDefinition.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
        factory.registerBeanDefinition("shutdownBean", shutdownDefinition);

        CloseBean closeBean = factory.getBean(CloseBean.class);
        ShutdownBean shutdownBean = factory.getBean(ShutdownBean.class);
        factory.destroySingletons();

        assertThat(closeBean.isClosed()).isTrue();
        assertThat(shutdownBean.isShutdown()).isTrue();
    }

    @Test
    void findsAndInvokesExplicitPublicDestroyMethod() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(CleanupBean.class);
        definition.setDestroyMethodName("cleanup");
        definition.setNonPublicAccessAllowed(false);
        factory.registerBeanDefinition("cleanupBean", definition);

        CleanupBean bean = factory.getBean(CleanupBean.class);
        factory.destroySingletons();

        assertThat(bean.isCleaned()).isTrue();
    }

    public static class CloseBean {
        private boolean closed;

        public void close() {
            this.closed = true;
        }

        public boolean isClosed() {
            return this.closed;
        }
    }

    public static class ShutdownBean {
        private boolean shutdown;

        public void shutdown() {
            this.shutdown = true;
        }

        public boolean isShutdown() {
            return this.shutdown;
        }
    }

    public static class CleanupBean {
        private boolean cleaned;

        public void cleanup() {
            this.cleaned = true;
        }

        public boolean isCleaned() {
            return this.cleaned;
        }
    }
}
