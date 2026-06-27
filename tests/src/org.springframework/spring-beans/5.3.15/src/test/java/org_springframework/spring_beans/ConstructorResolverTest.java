/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.security.Permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorResolverTest {

    @Test
    void autowireConstructorUsesPublicConstructorsWhenNonPublicAccessIsDisabled() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(PublicConstructorBean.class);
        beanDefinition.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
        beanDefinition.setNonPublicAccessAllowed(false);
        beanFactory.registerBeanDefinition("publicConstructorBean", beanDefinition);

        PublicConstructorBean bean = beanFactory.getBean(PublicConstructorBean.class);

        assertThat(bean.value()).isEqualTo("public");
    }

    @Test
    void autowireConstructorUsesDeclaredConstructorsWhenNonPublicAccessIsAllowed() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(DeclaredConstructorBean.class);
        beanDefinition.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
        beanFactory.registerBeanDefinition("declaredConstructorBean", beanDefinition);

        DeclaredConstructorBean bean = beanFactory.getBean(DeclaredConstructorBean.class);

        assertThat(bean.value()).isEqualTo("declared");
    }

    @Test
    void autowireConstructorResolvesEquivalentUserDeclaredConstructorForEnhancedClass() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(UserDeclaredConstructorBean$$SpringProxy.class);
        beanDefinition.getConstructorArgumentValues().addGenericArgumentValue("enhanced");
        beanFactory.registerBeanDefinition("enhancedConstructorBean", beanDefinition);

        UserDeclaredConstructorBean bean = beanFactory.getBean(UserDeclaredConstructorBean.class);

        assertThat(bean.value()).isEqualTo("enhanced");
    }

    @Test
    void factoryMethodResolutionUsesPublicFactoryMethodsWhenNonPublicAccessIsDisabled() {
        FactoryProduct product = createFactoryProduct("factoryProduct");

        assertThat(product.value()).isEqualTo("factory");
    }

    @Test
    @SuppressWarnings("removal")
    void factoryMethodResolutionUsesPublicFactoryMethodsWhenSecurityManagerIsInstalled() {
        SecurityManager previousSecurityManager = System.getSecurityManager();
        SecurityManager securityManager = new PermissiveSecurityManager();
        boolean securityManagerInstalled = installSecurityManagerIfSupported(securityManager);

        try {
            FactoryProduct product = createFactoryProduct("factoryProductWithSecurityManager");

            assertThat(product.value()).isEqualTo("factory");
            if (securityManagerInstalled) {
                assertThat(System.getSecurityManager()).isSameAs(securityManager);
            }
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    @Test
    void singleConstructorAutowiredArrayDependencyFallsBackToEmptyArray() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(ArrayDependencyBean.class);
        beanDefinition.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
        beanFactory.registerBeanDefinition("arrayDependencyBean", beanDefinition);

        ArrayDependencyBean bean = beanFactory.getBean(ArrayDependencyBean.class);

        assertThat(bean.dependencies()).isEmpty();
    }

    private static FactoryProduct createFactoryProduct(String beanName) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(PublicFactory.class);
        beanDefinition.setFactoryMethodName("create");
        beanDefinition.setNonPublicAccessAllowed(false);
        beanFactory.registerBeanDefinition(beanName, beanDefinition);
        return (FactoryProduct) beanFactory.getBean(beanName);
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

    public static class PublicConstructorBean {
        private final String value;

        public PublicConstructorBean() {
            this.value = "public";
        }

        public String value() {
            return value;
        }
    }

    static class DeclaredConstructorBean {
        private final String value;

        DeclaredConstructorBean() {
            this.value = "declared";
        }

        String value() {
            return value;
        }
    }

    public static class UserDeclaredConstructorBean {
        private final String value;

        public UserDeclaredConstructorBean(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    @SuppressWarnings("checkstyle:TypeName")
    public static class UserDeclaredConstructorBean$$SpringProxy extends UserDeclaredConstructorBean {
        public UserDeclaredConstructorBean$$SpringProxy(String value) {
            super(value);
        }
    }

    public static class PublicFactory {
        public static FactoryProduct create() {
            return new FactoryProduct("factory");
        }
    }

    public static class FactoryProduct {
        private final String value;

        FactoryProduct(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static class ArrayDependencyBean {
        private final Collaborator[] dependencies;

        public ArrayDependencyBean(Collaborator[] dependencies) {
            this.dependencies = dependencies;
        }

        public Collaborator[] dependencies() {
            return dependencies;
        }
    }

    public interface Collaborator {
    }
}
