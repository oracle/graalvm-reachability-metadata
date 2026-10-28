/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_assertj.assertj_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class ClassesTest {
    @Test
    void inspectsDeclaredAndInheritedFields() {
        assertThat(Product.class).hasPublicFields("sku", "name")
                .hasOnlyPublicFields("name", "sku")
                .hasDeclaredFields("name", "quantity")
                .hasOnlyDeclaredFields("quantity", "name");
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertThat(Product.class).hasDeclaredFields("sku"));
        assertThat(failure).hasMessageContaining("sku");
    }

    @Test
    void distinguishesDeclaredPublicAndInheritedMethods() {
        assertThat(Product.class).hasDeclaredMethods("label", "stock")
                .hasPublicMethods("label", "identifier")
                .hasMethods("stock", "identifier", "audit");
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertThat(Product.class).hasDeclaredMethods("identifier"));
        assertThat(failure).hasMessageContaining("identifier");
    }

    public static class Identified {
        public String sku;

        public String identifier() {
            return sku;
        }

        protected String audit() {
            return "inventory";
        }
    }

    public static class Product extends Identified {
        public String name;
        private int quantity;

        public String label() {
            return name;
        }

        private int stock() {
            return quantity;
        }
    }
}
