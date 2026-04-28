/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hamcrest.hamcrest_library;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class HasPropertyWithValueAnonymous1Test {

    @Test
    void propertyValueMatcherReadsBeanGetter() {
        Customer customer = new Customer("Ada Lovelace", true);

        MatcherAssert.assertThat(customer, Matchers.hasProperty("displayName", Matchers.equalTo("Ada Lovelace")));
        MatcherAssert.assertThat(customer, Matchers.hasProperty("active", Matchers.equalTo(true)));
    }

    public static final class Customer {
        private final String displayName;
        private final boolean active;

        public Customer(String displayName, boolean active) {
            this.displayName = displayName;
            this.active = active;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isActive() {
            return active;
        }
    }
}
