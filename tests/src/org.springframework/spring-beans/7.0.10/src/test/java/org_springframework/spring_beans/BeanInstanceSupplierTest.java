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

import static org.assertj.core.api.Assertions.assertThat;

public class BeanInstanceSupplierTest {
    @Test
    void invokesStaticFactoryWithAutowiredArguments() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        Ingredient ingredient = new Ingredient("cocoa");
        factory.registerSingleton("ingredient", ingredient);
        RootBeanDefinition definition = new RootBeanDefinition(Product.class);
        definition.setInstanceSupplier(BeanInstanceSupplier.<Product>forFactoryMethod(
                ProductFactory.class, "create", Ingredient.class));
        factory.registerBeanDefinition("product", definition);

        Product product = factory.getBean("product", Product.class);

        assertThat(product.ingredient()).isSameAs(ingredient);
        assertThat(product.label()).isEqualTo("static:cocoa");
    }

    @Test
    void invokesInstanceFactoryFromTheBeanFactory() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        Ingredient ingredient = new Ingredient("vanilla");
        factory.registerSingleton("ingredient", ingredient);
        factory.registerSingleton("productFactory", new ProductFactory("artisan"));
        RootBeanDefinition definition = new RootBeanDefinition(Product.class);
        definition.setFactoryBeanName("productFactory");
        definition.setInstanceSupplier(BeanInstanceSupplier.<Product>forFactoryMethod(
                ProductFactory.class, "mix", Ingredient.class));
        factory.registerBeanDefinition("product", definition);

        Product product = factory.getBean("product", Product.class);

        assertThat(product.ingredient()).isSameAs(ingredient);
        assertThat(product.label()).isEqualTo("artisan:vanilla");
    }

    public record Ingredient(String name) {
    }

    public record Product(String label, Ingredient ingredient) {
    }

    public static class ProductFactory {
        private final String prefix;

        public ProductFactory(String prefix) {
            this.prefix = prefix;
        }

        public static Product create(Ingredient ingredient) {
            return new Product("static:" + ingredient.name(), ingredient);
        }

        public Product mix(Ingredient ingredient) {
            return new Product(this.prefix + ":" + ingredient.name(), ingredient);
        }
    }
}
