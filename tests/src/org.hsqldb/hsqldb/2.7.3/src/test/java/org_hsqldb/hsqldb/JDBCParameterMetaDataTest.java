/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.jdbc.JDBCParameterMetaData;
import org.junit.jupiter.api.Test;

public class JDBCParameterMetaDataTest {
    @Test
    void describesPreparedStatementParameters() throws SQLException {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE ITEMS (
                        NAME VARCHAR(32) NOT NULL,
                        QUANTITY INTEGER NOT NULL
                    )
                    """);

            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT NAME FROM ITEMS WHERE NAME = ? AND QUANTITY > ?")) {
                ParameterMetaData parameterMetaData = preparedStatement.getParameterMetaData();
                JDBCParameterMetaData metadata = parameterMetaData.unwrap(
                        JDBCParameterMetaData.class);

                String description = metadata.toString();

                assertThat(metadata.getParameterCount()).isEqualTo(2);
                assertThat(description)
                        .startsWith(JDBCParameterMetaData.class.getName() + "@")
                        .contains("[")
                        .doesNotContain("MissingReflectionRegistration")
                        .doesNotContain("UnsupportedFeature");
            }
        }
    }

    private static Connection openConnection() throws SQLException {
        JDBCDataSource dataSource = new JDBCDataSource();
        String databaseName = "JDBCParameterMetaDataTest"
                + UUID.randomUUID().toString().replace("-", "");

        dataSource.setUrl("jdbc:hsqldb:mem:" + databaseName + ";shutdown=true");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }
}
