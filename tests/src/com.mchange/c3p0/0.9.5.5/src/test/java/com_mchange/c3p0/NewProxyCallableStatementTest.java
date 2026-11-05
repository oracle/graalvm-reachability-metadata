/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.C3P0ProxyStatement;
import org.junit.jupiter.api.Test;

import javax.sql.PooledConnection;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NewProxyCallableStatementTest {
    @Test
    void rawCallableQueryReturnsItsResult() throws Exception {
        PooledConnection pooled = C3p0Support.wrapper(false).getPooledConnection();
        try {
            try (Connection connection = pooled.getConnection();
                            CallableStatement statement = connection.prepareCall("CALL ABS(?)")) {
                statement.setInt(1, -55);
                C3P0ProxyStatement proxy = (C3P0ProxyStatement) statement;
                try (ResultSet rows = (ResultSet) proxy.rawStatementOperation(
                                PreparedStatement.class.getMethod("executeQuery"),
                                C3P0ProxyStatement.RAW_STATEMENT, new Object[0])) {
                    assertTrue(rows.next());
                    assertEquals(55, rows.getInt(1));
                    assertEquals(statement, rows.getStatement());
                }
            }
        } finally {
            pooled.close();
        }
    }
}
