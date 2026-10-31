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
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NewProxyConnectionTest {
    @Test
    void rawConnectionOperationReturnsManagedStatements() throws Exception {
        PooledConnection pooled = C3p0Support.wrapper(false).getPooledConnection();
        try {
            try (Connection connection = pooled.getConnection()) {
                C3P0ProxyConnection proxy = (C3P0ProxyConnection) connection;
                assertEquals(Boolean.TRUE, proxy.rawConnectionOperation(Connection.class.getMethod("getAutoCommit"),
                                C3P0ProxyConnection.RAW_CONNECTION, new Object[0]));
                try (Statement statement = (Statement) proxy.rawConnectionOperation(
                                Connection.class.getMethod("createStatement"),
                                C3P0ProxyConnection.RAW_CONNECTION, new Object[0])) {
                    assertEquals(connection, statement.getConnection());
                    try (var rows = statement.executeQuery("SELECT 52")) {
                        rows.next();
                        assertEquals(52, rows.getInt(1));
                    }
                }
            }
        } finally {
            pooled.close();
        }
    }
}
