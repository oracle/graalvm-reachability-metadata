/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
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
    void discoversPublicFactoryMethods() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(ProductFactory.class);
        definition.setFactoryMethodName("create");
        definition.setNonPublicAccessAllowed(false);
        factory.registerBeanDefinition("product", definition);

        assertThat(factory.getBean("product", Product.class).getName()).isEqualTo("factory");
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
