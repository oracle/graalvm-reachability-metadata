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

import javax.sql.StatementEvent;
import javax.sql.StatementEventListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DmdbPooledConnectionAnonymous2Test {
    @Test
    void forwardsStatementParametersAndReportsStatementClose() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:statementEvents");
        try (Connection physical = dataSource.getConnection()) {
            DmdbPooledConnection pooled = new DmdbPooledConnection(physical);
            List<StatementEvent> events = new ArrayList<>();
            pooled.addStatementEventListener(new StatementEventListener() {
                @Override
                public void statementClosed(StatementEvent event) {
                    events.add(event);
                }

                @Override
                public void statementErrorOccurred(StatementEvent event) {
                    throw new AssertionError(event.getSQLException());
                }
            });
            try (Connection logical = pooled.getConnection()) {
                PreparedStatement statement = logical.prepareStatement("select cast(? as integer) + 1");
                try (statement) {
                    statement.setInt(1, 41);
                    try (ResultSet rows = statement.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals(42, rows.getInt(1));
                    }
                }
                assertEquals(1, events.size());
                assertSame(statement, events.get(0).getStatement());
                assertSame(pooled, events.get(0).getSource());
                assertTrue(statement.isClosed());
            } finally {
                pooled.close();
            }
        }
    }
}
