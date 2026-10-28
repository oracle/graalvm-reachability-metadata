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

public class ObjectsTest {
    @Test
    void checksDeclaredFieldNamesWithoutIncludingStaticOrInheritedFields() {
        Product product = new Product("bolt", null);
        assertThat(product).hasOnlyFields("name", "alias");
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertThat(product).hasOnlyFields("name"));
        assertThat(failure).hasMessageContaining("alias");
    }

    @Test
    void checksNullableFieldsAcrossTheClassHierarchy() {
        Product product = new Product("bolt", null);
        assertThat(product).hasNoNullFieldsOrPropertiesExcept("alias");
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertThat(product).hasNoNullFieldsOrProperties());
        assertThat(failure).hasMessageContaining("alias");
        assertThat(new Product(null, null)).hasAllNullFieldsOrPropertiesExcept("id");
    }

    public static class Entity {
        private final String id = "inventory";
    }

    public static class Product extends Entity {
        public static final String CATEGORY = "hardware";
        private final String name;
        private final String alias;

        public Product(String name, String alias) {
            this.name = name;
            this.alias = alias;
        }
    }
}
