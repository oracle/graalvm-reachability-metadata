/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.calcite.interpreter.Row;
import org.apache.calcite.runtime.SqlFunctions;
import org.junit.jupiter.api.Test;

public class SqlFunctionsTest {
    @Test
    void accessesStructFieldsAcrossRuntimeRepresentations() {
        assertThat(SqlFunctions.structAccess(new Object[] {"array-name", 11}, 0, null))
                .isEqualTo("array-name");
        assertThat(SqlFunctions.structAccess(List.of("list-name", 22), 1, null))
                .isEqualTo(22);
        assertThat(SqlFunctions.structAccess(Row.of("row-name", 33), 0, null))
                .isEqualTo("row-name");

        StructRecord record = new StructRecord("bean-name", 44);

        assertThat(SqlFunctions.structAccess(record, 0, "name"))
                .isEqualTo("bean-name");
        assertThat(SqlFunctions.structAccess(record, 1, "quantity"))
                .isEqualTo(44);
    }

    public static class StructRecord {
        public final String name;
        public final int quantity;

        StructRecord(String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }
    }
}
