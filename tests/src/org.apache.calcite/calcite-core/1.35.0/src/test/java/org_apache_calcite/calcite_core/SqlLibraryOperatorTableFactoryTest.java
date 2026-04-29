/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.fun.SqlLibrary;
import org.apache.calcite.sql.fun.SqlLibraryOperatorTableFactory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlLibraryOperatorTableFactoryTest {
    @Test
    public void createsDialectOperatorTableFromLibraryOperators() {
        SqlOperatorTable operatorTable = SqlLibraryOperatorTableFactory.INSTANCE
                .getOperatorTable(SqlLibrary.MYSQL);

        List<String> operatorNames = operatorTable.getOperatorList().stream()
                .map(SqlOperator::getName)
                .collect(Collectors.toList());

        assertThat(operatorNames)
                .contains("GROUP_CONCAT", "JSON_TYPE", "REGEXP_REPLACE");
    }
}
