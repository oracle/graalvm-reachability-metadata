/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.rel.type.RelDataType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaTypeFactoryImplTest {
    @Test
    void createsStructFromPublicFields() {
        RelDataType type = new JavaTypeFactoryImpl().createStructType(Customer.class);

        assertThat(type.getFieldNames()).containsExactlyInAnyOrder("id", "name");
    }

    public static class Customer {
        public int id;
        public String name;
    }
}
