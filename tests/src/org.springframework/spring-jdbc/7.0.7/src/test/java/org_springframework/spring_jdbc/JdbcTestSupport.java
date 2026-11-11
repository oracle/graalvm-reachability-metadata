/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.h2.Driver;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

final class JdbcTestSupport {
    private JdbcTestSupport() {
    }

    static DataSource dataSource() {
        return new SimpleDriverDataSource(new Driver(), "jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
    }

    static int queryInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            if (!rows.next()) {
                throw new SQLException("Expected one result row");
            }
            return rows.getInt(1);
        }
    }

    static class TrackingDataSource extends DelegatingDataSource {
        private int connectionCount;
        private Connection lastConnection;

        TrackingDataSource() {
            super(dataSource());
        }

        @Override
        public Connection getConnection() throws SQLException {
            return record(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return record(super.getConnection(username, password));
        }

        private Connection record(Connection connection) {
            connectionCount++;
            lastConnection = connection;
            return connection;
        }

        int connectionCount() {
            return connectionCount;
        }

        Connection lastConnection() {
            return lastConnection;
        }
    }
}
