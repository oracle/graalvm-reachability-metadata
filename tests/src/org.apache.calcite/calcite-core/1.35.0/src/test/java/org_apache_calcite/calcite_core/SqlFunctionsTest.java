/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.runtime.SqlFunctions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlFunctionsTest {
    @Test
    void accessesNamedFieldOnRuntimeStruct() {
        Customer customer = new Customer(12, "Ada");

        assertThat(SqlFunctions.structAccess(customer, 1, "name")).isEqualTo("Ada");
        assertThat(SqlFunctions.structAccess(customer, 0, "id")).isEqualTo(12);
    }

    public static class Customer {
        public final int id;
        public final String name;

        public Customer(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
