/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.fun.SqlLibrary;
import org.apache.calcite.sql.fun.SqlLibraryOperatorTableFactory;
import org.junit.jupiter.api.Test;

public class SqlLibraryOperatorTableFactoryTest {
    @Test
    void loadsOperatorsDeclaredForCustomSqlLibrary() {
        SqlOperatorTable oracleTable = SqlLibraryOperatorTableFactory.INSTANCE.getOperatorTable(SqlLibrary.ORACLE);

        assertThat(operatorNames(oracleTable))
                .contains("DECODE", "NVL", "LPAD", "RTRIM");
    }

    @Test
    void chainsStandardOperatorsWithCustomLibraryOperators() {
        SqlOperatorTable combinedTable = SqlLibraryOperatorTableFactory.INSTANCE
                .getOperatorTable(SqlLibrary.STANDARD, SqlLibrary.ORACLE);

        assertThat(operatorNames(combinedTable))
                .contains("COUNT", "DECODE", "NVL");
    }

    private static List<String> operatorNames(SqlOperatorTable operatorTable) {
        return operatorTable.getOperatorList().stream()
                .map(SqlOperator::getName)
                .collect(Collectors.toList());
    }
}
