/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial.sqlite_jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class SQLiteConnectionTest {
    private static final String RESOURCE_DATABASE = "org_xerial/sqlite_jdbc/sqlite_connection_resource.db";

    @Test
    public void opensDatabaseFromClasspathResource() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setReadOnly(true);
        dataSource.setUrl("jdbc:sqlite::resource:" + RESOURCE_DATABASE);

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT name, value FROM connection_resource_fixture")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("name")).isEqualTo("resource-database");
            assertThat(resultSet.getInt("value")).isEqualTo(36);
            assertThat(resultSet.next()).isFalse();
        }
    }
}
