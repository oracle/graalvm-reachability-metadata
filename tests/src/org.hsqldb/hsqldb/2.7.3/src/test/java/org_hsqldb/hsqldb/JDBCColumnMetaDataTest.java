/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

public class JDBCColumnMetaDataTest {
    @Test
    void formatsColumnMetadataFromQueryResult() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:hsqldb:mem:column_metadata_reflection");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "VALUES (42)")) {
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            String columnLabel = resultSetMetaData.getColumnLabel(1);
            String description = resultSetMetaData.toString();

            assertThat(description)
                    .contains("column_1=[")
                    .contains("columnLabel=" + columnLabel)
                    .contains("columnName=")
                    .contains("columnType=");
        }
    }
}
