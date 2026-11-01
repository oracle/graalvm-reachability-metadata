/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.h2.jdbcx.JdbcDataSource;

public final class TestDatabase {
    private TestDatabase() {
    }

    public static JdbcDataSource source() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:springmetadata");
        source.setUser("sa");
        source.setPassword("");
        return source;
    }

    public static int query(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (ResultSet result = statement.executeQuery("select 42")) {
                if (!result.next()) {
                    throw new SQLException("Expected a query result");
                }
                return result.getInt(1);
            }
        }
    }
}
