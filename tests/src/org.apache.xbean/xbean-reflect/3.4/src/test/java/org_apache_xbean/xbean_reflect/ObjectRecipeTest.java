/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_reflect;

import org.apache.xbean.recipe.ObjectRecipe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectRecipeTest {
    @Test
    void invokesInstanceFactoryMethodAfterConstructingObject() {
        ObjectRecipe recipe = new ObjectRecipe(ProductBuilder.class, "build");

        Object value = recipe.create();

        assertThat(value).isInstanceOf(Product.class);
        assertThat(((Product) value).getDescription()).isEqualTo("created by instance factory");
    }

    public static class ProductBuilder {
        public Product build() {
            return new Product("created by instance factory");
        }
    }

    public static class Product {
        private final String description;

        public Product(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
