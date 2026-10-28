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

public class IntrospectionTest {
    @Test
    void extractsBeanAndBooleanPropertiesIncludingNestedGetters() {
        List<Customer> customers = List.of(new Customer("Ada", true), new Customer("Lin", false));
        assertThat(customers).extracting("name", "active", "address.city")
                .containsExactly(tuple("Ada", true, "Oslo"), tuple("Lin", false, "Oslo"));
        assertThat(customers).filteredOn("active", true).extracting("name").containsExactly("Ada");
    }

    @Test
    void rejectsNonPublicPropertyGetters() {
        IntrospectionError failure = assertThrows(IntrospectionError.class,
                () -> assertThat(new Customer("Ada", true)).extracting("secret"));
        assertThat(failure).hasMessageContaining("secret");
    }

    public static class Customer {
        private final String displayName;
        private final boolean enabled;

        public Customer(String displayName, boolean enabled) {
            this.displayName = displayName;
            this.enabled = enabled;
        }

        public String getName() {
            return displayName;
        }

        public boolean isActive() {
            return enabled;
        }

        public Address getAddress() {
            return new Address();
        }

        private String getSecret() {
            return "internal";
        }
    }

    public static class Address {
        public String getCity() {
            return "Oslo";
        }
    }
}
