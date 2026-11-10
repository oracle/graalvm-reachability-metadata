/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.lang.reflect.Executable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorResolverTest {
    @Test
    void resolvesDeclaredAndPublicConstructorsIncludingEmptyVarargs() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();

        RootBeanDefinition declaredDefinition = new RootBeanDefinition(VarargBean.class);
        declaredDefinition.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
        factory.registerBeanDefinition("declared", declaredDefinition);
        assertThat(factory.getBean("declared", VarargBean.class).getValues()).isEmpty();

        RootBeanDefinition publicDefinition = new RootBeanDefinition(RequiredBean.class);
        publicDefinition.setNonPublicAccessAllowed(false);
        publicDefinition.getConstructorArgumentValues().addGenericArgumentValue("argument");
        factory.registerBeanDefinition("public", publicDefinition);
        assertThat(factory.getBean("public", RequiredBean.class).getValue()).isEqualTo("argument");
    }

    @Test
    void mapsEnhancedSubclassConstructorToUserDeclaredConstructor() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(RequiredBean$$SpringProxy.class);
        definition.getConstructorArgumentValues().addGenericArgumentValue("enhanced");
        factory.registerBeanDefinition("enhanced", definition);

        RequiredBean bean = factory.getBean("enhanced", RequiredBean.class);

        assertThat(bean).isInstanceOf(RequiredBean$$SpringProxy.class);
        assertThat(bean.getValue()).isEqualTo("enhanced");
    }

    @Test
    void discoversPublicFactoryMethods() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(ProductFactory.class);
        definition.setFactoryMethodName("create");
        definition.setNonPublicAccessAllowed(false);
        factory.registerBeanDefinition("product", definition);

        assertThat(factory.getBean("product", Product.class).getName()).isEqualTo("factory");
    }

    @Test
    void createsBeansUsingPreferredPublicAndNonPublicConstructors() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("message", "injected");

        assertThat(factory.createBean(RequiredBean.class).getValue()).isEqualTo("injected");
        assertThat(factory.createBean(HiddenBean.class).value()).isEqualTo("injected");
        assertThat(factory.createBean(DefaultBean.class).value()).isEqualTo("default");
    }

    @Test
    void resolvesConstructorsForAotRegistrationWithBothAccessPolicies() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition publicDefinition = new RootBeanDefinition(RequiredBean.class);
        publicDefinition.setNonPublicAccessAllowed(false);
        publicDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, "public");
        factory.registerBeanDefinition("publicBean", publicDefinition);
        RootBeanDefinition hiddenDefinition = new RootBeanDefinition(HiddenBean.class);
        hiddenDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, "hidden");
        factory.registerBeanDefinition("hiddenBean", hiddenDefinition);

        Executable publicConstructor = RegisteredBean.of(factory, "publicBean")
                .resolveConstructorOrFactoryMethod();
        Executable hiddenConstructor = RegisteredBean.of(factory, "hiddenBean")
                .resolveConstructorOrFactoryMethod();

        assertThat(publicConstructor.getDeclaringClass()).isEqualTo(RequiredBean.class);
        assertThat(publicConstructor.getParameterTypes()).containsExactly(String.class);
        assertThat(hiddenConstructor.getDeclaringClass()).isEqualTo(HiddenBean.class);
        assertThat(hiddenConstructor.getParameterTypes()).containsExactly(String.class);
        assertThat(factory.getBean("publicBean", RequiredBean.class).getValue()).isEqualTo("public");
        assertThat(factory.getBean("hiddenBean", HiddenBean.class).value()).isEqualTo("hidden");
    }

    private static class HiddenBean {
        private final String value;

        private HiddenBean(String value) {
            this.value = value;
        }

        public String value() {
            return this.value;
        }
    }

    public static class DefaultBean {
        public DefaultBean() {
        }

        public String value() {
            return "default";
        }
    }

    public static class VarargBean {
        private final String[] values;

        public VarargBean(String... values) {
            this.values = values;
        }

        public String[] getValues() {
            return this.values;
        }
    }

    public static class RequiredBean {
        private final String value;

        public RequiredBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }
    }

    @SuppressWarnings("checkstyle:TypeName")
    public static class RequiredBean$$SpringProxy extends RequiredBean {
        public RequiredBean$$SpringProxy(String value) {
            super(value);
        }
    }

    public static class ProductFactory {
        public static Product create() {
            return new Product("factory");
        }
    }

    public static class Product {
        private final String name;

        public Product(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }
}
