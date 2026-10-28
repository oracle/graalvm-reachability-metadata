/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_assertj.assertj_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.assertj.core.util.introspection.IntrospectionError;
import org.junit.jupiter.api.Test;

public class FieldUtilsTest {
    @Test
    void extractsPrivateInheritedAndPublicFields() {
        List<Product> products = List.of(new Product("bolt", 12), new Product("nut", 24));
        assertThat(products).extracting("name", "quantity", "warehouse")
                .containsExactly(tuple("bolt", 12, "north"), tuple("nut", 24, "north"));
        assertThat(products).filteredOn("quantity", 24).extracting("name").containsExactly("nut");
    }

    @Test
    void doesNotExtractStaticInterfaceFieldsAsInstanceState() {
        IntrospectionError failure = assertThrows(IntrospectionError.class,
                () -> assertThat(new Product("bolt", 12)).extracting("category"));
        assertThat(failure).hasMessageContaining("category").getRootCause()
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("static");
    }

    public interface Categorized {
        String category = "hardware";
    }

    public static class Stock {
        private final String warehouse = "north";
    }

    public static class Product extends Stock implements Categorized {
        public final String name;
        private final int quantity;

        public Product(String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }
    }
}
