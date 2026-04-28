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

public class SamePropertyValuesAsTest {

    @Test
    void samePropertyValuesMatcherReadsExpectedAndActualBeanProperties() {
        Customer expected = new Customer("Ada Lovelace", 37, true);
        Customer actual = new Customer("Ada Lovelace", 37, true);

        MatcherAssert.assertThat(actual, Matchers.samePropertyValuesAs(expected));
    }

    @Test
    void samePropertyValuesMatcherReportsMismatchedBeanProperty() {
        Customer expected = new Customer("Ada Lovelace", 37, true);
        Customer actual = new Customer("Grace Hopper", 37, true);

        MatcherAssert.assertThat(actual, Matchers.not(Matchers.samePropertyValuesAs(expected)));
    }

    public static final class Customer {
        private final String displayName;
        private final int age;
        private final boolean active;

        public Customer(String displayName, int age, boolean active) {
            this.displayName = displayName;
            this.age = age;
            this.active = active;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getAge() {
            return age;
        }

        public boolean isActive() {
            return active;
        }
    }
}
