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

public class ConstructorResolverTest {

    @Test
    public void autowireConstructorUsesPublicConstructorLookupWhenNonPublicAccessIsDisabled() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("dependency", new RootBeanDefinition(ConstructorDependency.class));
        RootBeanDefinition beanDefinition = constructorAutowiredBeanDefinition(PublicConstructorAutowiredBean.class);
        beanDefinition.setNonPublicAccessAllowed(false);
        beanFactory.registerBeanDefinition("publicConstructorBean", beanDefinition);

        PublicConstructorAutowiredBean bean = beanFactory.getBean(
                "publicConstructorBean", PublicConstructorAutowiredBean.class);

        assertThat(bean.getDependency()).isNotNull();
    }

    @Test
    public void autowireConstructorUsesDeclaredConstructorLookupForNonPublicConstructors() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = constructorAutowiredBeanDefinition(PrivateConstructorBean.class);
        beanFactory.registerBeanDefinition("privateConstructorBean", beanDefinition);

        PrivateConstructorBean bean = beanFactory.getBean("privateConstructorBean", PrivateConstructorBean.class);

        assertThat(bean.getMessage()).isEqualTo("private");
    }

    @Test
    public void constructorResolutionMapsCglibNamedSubclassConstructorToUserDeclaredConstructor() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = constructorAutowiredBeanDefinition(UserBean$$Generated.class);
        beanDefinition.getConstructorArgumentValues().addGenericArgumentValue("proxied");
        beanFactory.registerBeanDefinition("generatedUserBean", beanDefinition);

        UserBean bean = beanFactory.getBean("generatedUserBean", UserBean.class);

        assertThat(bean.getMessage()).isEqualTo("proxied");
    }

    @Test
    public void factoryMethodResolutionUsesPublicMethodLookupWhenNonPublicAccessIsDisabled() {
        FactoryCreatedBean bean = createFactoryMethodBean(false);

        assertThat(bean.getMessage()).isEqualTo("created");
    }

    @Test
    @SuppressWarnings("removal")
    public void factoryMethodResolutionUsesPrivilegedPublicMethodLookupWithSecurityManager() {
        SecurityManager previousSecurityManager = System.getSecurityManager();
        SecurityManager securityManager = new PermissiveSecurityManager();
        boolean securityManagerInstalled = installSecurityManager(securityManager);

        try {
            FactoryCreatedBean bean = createFactoryMethodBean(false);

            assertThat(bean.getMessage()).isEqualTo("created");
            assertThat(securityManagerInstalled).isEqualTo(System.getSecurityManager() == securityManager);
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    @Test
    public void singleConstructorAutowiringFallsBackToEmptyArrayWhenNoArrayElementsExist() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = constructorAutowiredBeanDefinition(ArrayAutowiredBean.class);
        beanFactory.registerBeanDefinition("arrayAutowiredBean", beanDefinition);

        ArrayAutowiredBean bean = beanFactory.getBean("arrayAutowiredBean", ArrayAutowiredBean.class);

        assertThat(bean.getDependencies()).isEmpty();
    }

    private static RootBeanDefinition constructorAutowiredBeanDefinition(Class<?> beanClass) {
        RootBeanDefinition beanDefinition = new RootBeanDefinition(beanClass);
        beanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
        return beanDefinition;
    }

    private static FactoryCreatedBean createFactoryMethodBean(boolean nonPublicAccessAllowed) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(PublicStaticFactory.class);
        beanDefinition.setFactoryMethodName("create");
        beanDefinition.setNonPublicAccessAllowed(nonPublicAccessAllowed);
        beanFactory.registerBeanDefinition("factoryCreatedBean", beanDefinition);
        return beanFactory.getBean("factoryCreatedBean", FactoryCreatedBean.class);
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

    public static class ConstructorDependency {
    }

    public static class PublicConstructorAutowiredBean {
        private final ConstructorDependency dependency;

        public PublicConstructorAutowiredBean(ConstructorDependency dependency) {
            this.dependency = dependency;
        }

        public ConstructorDependency getDependency() {
            return dependency;
        }
    }

    public static class PrivateConstructorBean {
        private final String message;

        private PrivateConstructorBean() {
            this.message = "private";
        }

        public String getMessage() {
            return message;
        }
    }

    public static class UserBean {
        private final String message;

        public UserBean(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    // CheckStyle: start generated
    public static class UserBean$$Generated extends UserBean {

        public UserBean$$Generated(String message) {
            super(message);
        }
    }
    // CheckStyle: stop generated

    public static class PublicStaticFactory {

        public static FactoryCreatedBean create() {
            return new FactoryCreatedBean("created");
        }
    }

    public static class FactoryCreatedBean {
        private final String message;

        public FactoryCreatedBean(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class ArrayAutowiredBean {
        private final ConstructorDependency[] dependencies;

        public ArrayAutowiredBean(ConstructorDependency[] dependencies) {
            this.dependencies = dependencies;
        }

        public ConstructorDependency[] getDependencies() {
            return dependencies;
        }
    }

    @SuppressWarnings("removal")
    private static final class PermissiveSecurityManager extends SecurityManager {

        @Override
        public void checkPermission(Permission permission) {
        }
    }
}
