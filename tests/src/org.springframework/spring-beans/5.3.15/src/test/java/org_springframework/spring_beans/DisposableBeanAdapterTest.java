/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class DisposableBeanAdapterTest {

    @Test
    public void inferredCloseMethodIsInvokedAsCustomDestroyMethod() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(PublicCloseMethodBean.class);
        beanDefinition.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
        beanFactory.registerBeanDefinition("closeMethodBean", beanDefinition);
        PublicCloseMethodBean bean = beanFactory.getBean("closeMethodBean", PublicCloseMethodBean.class);

        beanFactory.destroySingletons();

        assertThat(bean.isClosed()).isTrue();
    }

    @Test
    public void inferredShutdownMethodIsInvokedAsCustomDestroyMethod() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(PublicShutdownMethodBean.class);
        beanDefinition.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
        beanFactory.registerBeanDefinition("shutdownMethodBean", beanDefinition);
        PublicShutdownMethodBean bean = beanFactory.getBean("shutdownMethodBean", PublicShutdownMethodBean.class);

        beanFactory.destroySingletons();

        assertThat(bean.isShutdown()).isTrue();
    }

    @Test
    public void explicitDestroyMethodUsesPublicMethodLookupWhenNonPublicAccessIsDisabled() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = customCleanupBeanDefinition();
        beanDefinition.setNonPublicAccessAllowed(false);
        beanFactory.registerBeanDefinition("customCleanupBean", beanDefinition);
        PublicCustomCleanupBean bean = beanFactory.getBean("customCleanupBean", PublicCustomCleanupBean.class);

        beanFactory.destroySingletons();

        assertThat(bean.isCleaned()).isTrue();
    }

    @Test
    @SuppressWarnings("removal")
    public void customDestroyMethodIsInvokedWithSecurityManager() {
        SecurityManager previousSecurityManager = System.getSecurityManager();
        SecurityManager securityManager = new PermissiveSecurityManager();
        boolean securityManagerInstalled = installSecurityManager(securityManager);

        try {
            if (securityManagerInstalled) {
                assertThat(System.getSecurityManager()).isSameAs(securityManager);
            }
            DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
            beanFactory.registerBeanDefinition("privilegedCleanupBean", customCleanupBeanDefinition());
            PublicCustomCleanupBean bean = beanFactory.getBean("privilegedCleanupBean", PublicCustomCleanupBean.class);

            beanFactory.destroySingletons();

            assertThat(bean.isCleaned()).isTrue();
            assertThat(securityManagerInstalled).isEqualTo(System.getSecurityManager() == securityManager);
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    private static RootBeanDefinition customCleanupBeanDefinition() {
        RootBeanDefinition beanDefinition = new RootBeanDefinition(PublicCustomCleanupBean.class);
        beanDefinition.setDestroyMethodName("cleanup");
        return beanDefinition;
    }

    @SuppressWarnings("removal")
    private static boolean installSecurityManager(SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return System.getSecurityManager() == securityManager;
        } catch (UnsupportedOperationException | SecurityException ex) {
            return false;
        }
    }

    public static class PublicCloseMethodBean {
        private boolean closed;

        public void close() {
            this.closed = true;
        }

        public boolean isClosed() {
            return closed;
        }
    }

    public static class PublicShutdownMethodBean {
        private boolean shutdown;

        public void shutdown() {
            this.shutdown = true;
        }

        public boolean isShutdown() {
            return shutdown;
        }
    }

    public static class PublicCustomCleanupBean {
        private boolean cleaned;

        public void cleanup() {
            this.cleaned = true;
        }

        public boolean isCleaned() {
            return cleaned;
        }
    }

    @SuppressWarnings("removal")
    private static final class PermissiveSecurityManager extends SecurityManager {

        @Override
        public void checkPermission(Permission permission) {
        }
    }
}
