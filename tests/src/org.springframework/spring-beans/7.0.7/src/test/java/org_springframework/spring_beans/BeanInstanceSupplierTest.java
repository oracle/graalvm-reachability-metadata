/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanInstanceSupplierTest {
    @Test
    void invokesStaticFactoryWithAutowiredArguments() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("name", "supplied");
        RootBeanDefinition definition = new RootBeanDefinition(Product.class);
        definition.setInstanceSupplier(BeanInstanceSupplier.forFactoryMethod(
                ProductFactory.class, "create", String.class));
        factory.registerBeanDefinition("product", definition);
        assertEquals("supplied", factory.getBean("product", Product.class).name());
    }

    public record Product(String name) {
    }

    public static class ProductFactory {
        public static Product create(String name) {
            return new Product(name);
        }
    }
}
