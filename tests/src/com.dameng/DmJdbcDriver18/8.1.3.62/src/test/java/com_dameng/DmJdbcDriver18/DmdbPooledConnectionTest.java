/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_dameng.DmJdbcDriver18;

import dm.jdbc.driver.DmdbPooledConnection;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DmdbPooledConnectionTest {
    @Test
    void wrapsRealJdbcConnectionsAndPreparedStatements() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:pooled");
        try (Connection physical = dataSource.getConnection()) {
            DmdbPooledConnection pooled = new DmdbPooledConnection(physical);
            try (Connection logical = pooled.getConnection()) {
                try (Statement schema = logical.createStatement()) {
                    schema.executeUpdate("create table inventory (id integer primary key, name varchar(40))");
                }
                try (PreparedStatement insert = logical.prepareStatement("insert into inventory values (?, ?)")) {
                    insert.setInt(1, 1);
                    insert.setString(2, "widget");
                    assertEquals(1, insert.executeUpdate());
                }
                try (PreparedStatement select = logical.prepareStatement("select name from inventory where id = ?")) {
                    select.setInt(1, 1);
                    try (ResultSet rows = select.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals("widget", rows.getString(1));
                        assertFalse(rows.next());
                    }
                }
            } finally {
                pooled.close();
            }
            assertTrue(physical.isClosed());
        }
    }
}
