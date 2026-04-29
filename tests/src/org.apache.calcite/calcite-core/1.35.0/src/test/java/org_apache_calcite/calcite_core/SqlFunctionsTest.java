/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.runtime.SqlFunctions;
import org.apache.calcite.sql.SqlKind;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlFunctionsTest {
    @Test
    public void accessesStructFieldByNameForRuntimeBeanObjects() {
        Object value = SqlFunctions.structAccess(SqlKind.SELECT, 0, "sql");

        assertThat(value).isEqualTo("SELECT");
    }
}
