/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.calcite.jdbc.Driver;
import org.junit.jupiter.api.Test;

public class CalciteMetaImplTest {
    @Test
    void databaseMetadataExposesTableTypesFromCalciteMetaResultSet() throws SQLException {
        try (Connection connection = new Driver()
                .connect(Driver.CONNECT_STRING_PREFIX, new Properties())) {
            assertThat(connection).isNotNull();

            try (ResultSet tableTypes = connection.getMetaData().getTableTypes()) {
                assertThat(readStrings(tableTypes, "TABLE_TYPE"))
                        .containsExactly("TABLE", "VIEW");
            }
        }
    }

    private static List<String> readStrings(ResultSet resultSet, String columnLabel)
            throws SQLException {
        List<String> values = new ArrayList<>();
        while (resultSet.next()) {
            values.add(resultSet.getString(columnLabel));
        }
        return values;
    }
}
