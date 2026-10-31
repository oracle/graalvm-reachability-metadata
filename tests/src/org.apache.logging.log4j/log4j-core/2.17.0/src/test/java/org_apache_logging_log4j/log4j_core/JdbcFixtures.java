/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class JdbcFixtures {
    private JdbcFixtures() {
    }

    public static DataSource dataSource() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:factory");
        return source;
    }

    public static Connection connection() throws SQLException {
        return dataSource().getConnection();
    }

    static void assertConnected(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (ResultSet rows = statement.executeQuery("SELECT 'connected'")) {
                assertTrue(rows.next());
                assertEquals("connected", rows.getString(1));
            }
        }
    }
}
