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
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class AbstractAutowireCapableBeanFactoryTest {

    @Test
    @SuppressWarnings("removal")
    public void customInitMethodIsInvokedWithoutSecurityManager() {
        SecurityManager previousSecurityManager = System.getSecurityManager();
        boolean securityManagerCleared = setSecurityManager(null);

        try {
            InitMethodBean bean = createBeanWithCustomInitMethod("customInitBean");

            assertThat(bean.isInitialized()).isTrue();
            assertThat(System.getSecurityManager()).isNull();
        } finally {
            if (securityManagerCleared) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    @Test
    @SuppressWarnings("removal")
    public void customInitMethodIsInvokedWithSecurityManager() {
        SecurityManager previousSecurityManager = System.getSecurityManager();
        SecurityManager securityManager = new PermissiveSecurityManager();
        boolean securityManagerInstalled = setSecurityManager(securityManager);

        try {
            if (securityManagerInstalled) {
                assertThat(System.getSecurityManager()).isSameAs(securityManager);
            }

            InitMethodBean bean = createBeanWithCustomInitMethod("privilegedCustomInitBean");

            assertThat(bean.isInitialized()).isTrue();
            assertThat(securityManagerInstalled).isEqualTo(System.getSecurityManager() == securityManager);
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    private static InitMethodBean createBeanWithCustomInitMethod(String beanName) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(InitMethodBean.class);
        beanDefinition.setInitMethodName("initialize");
        beanFactory.registerBeanDefinition(beanName, beanDefinition);
        return beanFactory.getBean(beanName, InitMethodBean.class);
    }

    @SuppressWarnings("removal")
    private static boolean setSecurityManager(SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return System.getSecurityManager() == securityManager;
        } catch (UnsupportedOperationException | SecurityException ex) {
            return false;
        }
    }

    public static class InitMethodBean {
        private boolean initialized;

        public void initialize() {
            initialized = true;
        }

        public boolean isInitialized() {
            return initialized;
        }
    }

    @SuppressWarnings("removal")
    private static final class PermissiveSecurityManager extends SecurityManager {

        @Override
        public void checkPermission(Permission permission) {
        }
    }
}
