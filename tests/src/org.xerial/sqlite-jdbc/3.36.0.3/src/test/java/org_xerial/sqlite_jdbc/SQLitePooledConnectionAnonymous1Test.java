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

import javax.sql.PooledConnection;

import org.junit.jupiter.api.Test;
import org.sqlite.javax.SQLiteConnectionPoolDataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class SQLitePooledConnectionAnonymous1Test {
    @Test
    public void pooledConnectionProxyDelegatesConnectionMethodsToPhysicalConnection() throws Exception {
        SQLiteConnectionPoolDataSource dataSource = new SQLiteConnectionPoolDataSource();
        PooledConnection pooledConnection = dataSource.getPooledConnection();
        Connection connection = pooledConnection.getConnection();
        try {
            assertThat(connection.isClosed()).isFalse();
            assertThat(connection.getAutoCommit()).isTrue();

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE pooled_connection_test (value INTEGER NOT NULL)");
                statement.executeUpdate("INSERT INTO pooled_connection_test (value) VALUES (42)");

                try (ResultSet resultSet = statement.executeQuery("SELECT value FROM pooled_connection_test")) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt("value")).isEqualTo(42);
                    assertThat(resultSet.next()).isFalse();
                }
            }
        } finally {
            connection.close();
            pooledConnection.close();
        }
    }
}
