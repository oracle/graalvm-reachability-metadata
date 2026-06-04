/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class EqualsBuilderTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void refEqMatcherComparesArgumentsByTheirFields() {
        CustomerRepository repository = Mockito.mock(CustomerRepository.class);
        Customer actualCustomer =
                new Customer("Ada", 42, "actual audit value", "actual excluded value");

        repository.save(actualCustomer);

        Customer expectedCustomer =
                new Customer("Ada", 42, "different audit value", "expected excluded value");
        Mockito.verify(repository).save(ArgumentMatchers.refEq(expectedCustomer, "excludedValue"));
    }

    private interface CustomerRepository {
        void save(Customer customer);
    }

    private static class PersonRecord {
        private final String name;

        PersonRecord(String name) {
            this.name = name;
        }
    }

    private static final class Customer extends PersonRecord {
        private static final String IGNORED_STATIC_VALUE = "ignored";

        private final int loyaltyPoints;
        private final int[] favoriteNumbers;
        private final String excludedValue;
        private transient String auditValue;

        Customer(String name, int loyaltyPoints, String auditValue, String excludedValue) {
            super(name);
            this.loyaltyPoints = loyaltyPoints;
            this.favoriteNumbers = new int[] {1, 1, 2, 3, 5};
            this.auditValue = auditValue;
            this.excludedValue = excludedValue;
        }
    }
}
