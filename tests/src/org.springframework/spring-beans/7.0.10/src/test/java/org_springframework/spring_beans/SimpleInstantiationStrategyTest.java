/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleInstantiationStrategyTest {
    @Test
    void instantiatesDefaultConstructorAndInvokesFactoryMethod() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerBeanDefinition("defaultProduct", new RootBeanDefinition(Product.class));

        RootBeanDefinition factoryDefinition = new RootBeanDefinition(ProductFactory.class);
        factoryDefinition.setFactoryMethodName("create");
        factory.registerBeanDefinition("factoryProduct", factoryDefinition);

        assertThat(factory.getBean("defaultProduct", Product.class).getName()).isEqualTo("default");
        assertThat(factory.getBean("factoryProduct", Product.class).getName()).isEqualTo("factory");
    }

    public static class ProductFactory {
        public static Product create() {
            return new Product("factory");
        }
    }

    public static class Product {
        private final String name;

        public Product() {
            this("default");
        }

        public Product(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }
}
