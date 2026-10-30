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

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DmdbPooledConnectionAnonymous1Test {
    @Test
    void forwardsConnectionOperationsAndReportsLogicalClose() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:connectionEvents");
        try (Connection physical = dataSource.getConnection()) {
            DmdbPooledConnection pooled = new DmdbPooledConnection(physical);
            List<ConnectionEvent> events = new ArrayList<>();
            pooled.addConnectionEventListener(new ConnectionEventListener() {
                @Override
                public void connectionClosed(ConnectionEvent event) {
                    events.add(event);
                }

                @Override
                public void connectionErrorOccurred(ConnectionEvent event) {
                    throw new AssertionError(event.getSQLException());
                }
            });
            try {
                try (Connection logical = pooled.getConnection()) {
                    logical.setAutoCommit(false);
                    assertFalse(logical.getAutoCommit());
                    assertFalse(physical.getAutoCommit());
                    logical.rollback();
                }
                assertEquals(1, events.size());
                assertSame(pooled, events.get(0).getSource());
                assertFalse(physical.isClosed());
            } finally {
                pooled.close();
            }
        }
    }
}
