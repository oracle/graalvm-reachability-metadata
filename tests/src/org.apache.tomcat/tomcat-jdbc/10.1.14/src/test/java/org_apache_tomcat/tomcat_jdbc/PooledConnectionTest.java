/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_jdbc;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class PooledConnectionTest {

    @Test
    void opensConnectionWithConfiguredDriverClass() throws SQLException {
        PoolProperties poolProperties = createPoolProperties();
        ConnectionPool connectionPool = new ConnectionPool(poolProperties);
        PooledConnection pooledConnection = new PooledConnection(poolProperties, connectionPool);
        try {
            pooledConnection.connect();
            try (Connection connection = pooledConnection.getConnection();
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
            pooledConnection.release();
        }
    }

    private PoolProperties createPoolProperties() {
        PoolProperties poolProperties = new PoolProperties();
        poolProperties.setDriverClassName("org.h2.Driver");
        poolProperties.setUrl("jdbc:h2:mem:pooled_connection");
        poolProperties.setUsername("sa");
        poolProperties.setPassword("");
        poolProperties.setInitialSize(0);
        poolProperties.setMaxActive(1);
        poolProperties.setJmxEnabled(false);
        poolProperties.setTimeBetweenEvictionRunsMillis(0);
        return poolProperties;
    }

    private void assertInsertedItem(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("SELECT name FROM pooled_connection_item WHERE id = 1")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).isEqualTo("driver-connection");
            assertThat(resultSet.next()).isFalse();
        }
    }
}
