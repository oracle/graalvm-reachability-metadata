/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.C3P0ProxyConnection;
import org.junit.jupiter.api.Test;

import javax.sql.PooledConnection;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class C3P0PooledConnectionInnerProxyConnectionInvocationHandlerTest {
    @Test
    void traditionalConnectionDelegatesJdbcAndRawOperations() throws Exception {
        PooledConnection pooled = C3p0Support.wrapper(true).getPooledConnection();
        try {
            try (Connection connection = pooled.getConnection()) {
                assertNotNull(connection.toString());
                assertTrue(connection.getAutoCommit());
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                assertEquals(Connection.TRANSACTION_SERIALIZABLE, connection.getTransactionIsolation());
                connection.setCatalog(connection.getCatalog());
                connection.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT);
                assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, connection.getHoldability());
                assertNotNull(connection.getMetaData().getDatabaseProductName());
                C3p0Support.assertQuery(connection, 48);
                try (PreparedStatement statement = connection.prepareStatement("SELECT ?")) {
                    statement.setInt(1, 49);
                    try (ResultSet rows = statement.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals(49, rows.getInt(1));
                    }
                }
                try (CallableStatement statement = connection.prepareCall("CALL ABS(?)")) {
                    statement.setInt(1, -50);
                    try (ResultSet rows = statement.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals(50, rows.getInt(1));
                    }
                }
                C3P0ProxyConnection proxy = (C3P0ProxyConnection) connection;
                assertEquals(Boolean.TRUE, proxy.rawConnectionOperation(Connection.class.getMethod("getAutoCommit"),
                                C3P0ProxyConnection.RAW_CONNECTION, new Object[0]));
            }
            try (Connection connection = pooled.getConnection()) {
                assertEquals(Connection.TRANSACTION_READ_COMMITTED, connection.getTransactionIsolation());
            }
        } finally {
            pooled.close();
        }
    }
}
