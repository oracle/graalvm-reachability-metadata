/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.jdbc.Driver;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class CalciteMetaImplTest {
    @Test
    void exposesJdbcTypeMetadata() throws Exception {
        try (Connection connection = new Driver().connect("jdbc:calcite:", new Properties());
                ResultSet resultSet = connection.getMetaData().getTypeInfo()) {
            List<String> typeNames = new ArrayList<>();
            while (resultSet.next()) {
                typeNames.add(resultSet.getString("TYPE_NAME"));
            }

            assertThat(typeNames).contains("INTEGER", "VARCHAR", "BOOLEAN");
        }
    }
}
