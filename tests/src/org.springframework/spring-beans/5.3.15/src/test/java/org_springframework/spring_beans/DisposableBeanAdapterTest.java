/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.security.Permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class DisposableBeanAdapterTest {

    @Test
    void inferredCloseMethodIsInvokedOnSingletonDestruction() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(InferredCloseBean.class);
        beanDefinition.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
        beanFactory.registerBeanDefinition("inferredCloseBean", beanDefinition);

        InferredCloseBean bean = beanFactory.getBean(InferredCloseBean.class);
        beanFactory.destroySingletons();

        assertThat(bean.destroyed()).isTrue();
    }

    @Test
    void inferredShutdownMethodIsInvokedWhenCloseIsAbsent() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(InferredShutdownBean.class);
        beanDefinition.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
        beanFactory.registerBeanDefinition("inferredShutdownBean", beanDefinition);

        InferredShutdownBean bean = beanFactory.getBean(InferredShutdownBean.class);
        beanFactory.destroySingletons();

        assertThat(bean.destroyed()).isTrue();
    }

    @Test
    void publicDestroyMethodIsResolvedWhenNonPublicAccessIsDisabled() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(PublicDestroyMethodBean.class);
        beanDefinition.setDestroyMethodName("release");
        beanDefinition.setNonPublicAccessAllowed(false);
        beanFactory.registerBeanDefinition("publicDestroyMethodBean", beanDefinition);

        PublicDestroyMethodBean bean = beanFactory.getBean(PublicDestroyMethodBean.class);
        beanFactory.destroySingletons();

        assertThat(bean.destroyed()).isTrue();
    }

    @Test
    @SuppressWarnings("removal")
    void customDestroyMethodCanRunWithSecurityManager() {
        SecurityManager previousSecurityManager = System.getSecurityManager();
        SecurityManager securityManager = new PermissiveSecurityManager();
        boolean securityManagerInstalled = installSecurityManagerIfSupported(securityManager);

        try {
            SecurityManagedDestroyBean bean = createAndDestroySecurityManagedBean();

            assertThat(bean.destroyed()).isTrue();
            if (securityManagerInstalled) {
                assertThat(System.getSecurityManager()).isSameAs(securityManager);
            }
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    private static SecurityManagedDestroyBean createAndDestroySecurityManagedBean() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(SecurityManagedDestroyBean.class);
        beanDefinition.setDestroyMethodName("finish");
        beanFactory.registerBeanDefinition("securityManagedDestroyBean", beanDefinition);

        SecurityManagedDestroyBean bean = beanFactory.getBean(SecurityManagedDestroyBean.class);
        beanFactory.destroySingletons();
        return bean;
    }

    @SuppressWarnings("removal")
    private static boolean installSecurityManagerIfSupported(SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return System.getSecurityManager() == securityManager;
        } catch (UnsupportedOperationException unsupportedOperationException) {
            return false;
        }
    }

    @SuppressWarnings("removal")
    private static class PermissiveSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
        }

        @Override
        public void checkPermission(Permission permission, Object context) {
        }
    }

    public static class InferredCloseBean {
        private boolean destroyed;

        public void close() {
            this.destroyed = true;
        }

        public boolean destroyed() {
            return destroyed;
        }
    }

    public static class InferredShutdownBean {
        private boolean destroyed;

        public void shutdown() {
            this.destroyed = true;
        }

        public boolean destroyed() {
            return destroyed;
        }
    }

    public static class PublicDestroyMethodBean {
        private boolean destroyed;

        public void release() {
            this.destroyed = true;
        }

        public boolean destroyed() {
            return destroyed;
        }
    }

    public static class SecurityManagedDestroyBean {
        private boolean destroyed;

        public void finish() {
            this.destroyed = true;
        }

        public boolean destroyed() {
            return destroyed;
        }
    }
}
