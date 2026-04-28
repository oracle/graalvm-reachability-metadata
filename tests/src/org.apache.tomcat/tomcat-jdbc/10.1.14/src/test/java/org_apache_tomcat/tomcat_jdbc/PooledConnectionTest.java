/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_jdbc;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class PooledConnectionTest {

    @Test
    void opensConnectionWithConfiguredDriverClass() throws SQLException {
        DataSource dataSource = createDataSource();
        try {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE pooled_connection_item (
                            id INT NOT NULL PRIMARY KEY,
                            name VARCHAR(255)
                        )
                        """);
                statement.executeUpdate("""
                        INSERT INTO pooled_connection_item(id, name)
                        VALUES (1, 'driver-connection')
                        """);

                assertInsertedItem(statement);
            }
        } finally {
            dataSource.close(true);
        }
    }

    private DataSource createDataSource() {
        DataSource dataSource = new DataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:pooled_connection");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(0);
        dataSource.setMaxActive(1);
        return dataSource;
    }

    private void assertInsertedItem(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("SELECT name FROM pooled_connection_item WHERE id = 1")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).isEqualTo("driver-connection");
            assertThat(resultSet.next()).isFalse();
        }
    }
}
