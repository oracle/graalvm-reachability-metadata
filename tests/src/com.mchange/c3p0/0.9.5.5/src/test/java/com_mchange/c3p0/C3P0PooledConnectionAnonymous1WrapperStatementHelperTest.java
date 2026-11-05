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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class C3P0PooledConnectionAnonymous1WrapperStatementHelperTest {
    @Test
    void traditionalStatementWrapsRawQueryResults() throws Exception {
        PooledConnection pooled = C3p0Support.wrapper(true).getPooledConnection();
        try {
            try (Connection connection = pooled.getConnection(); Statement statement = connection.createStatement()) {
                C3P0ProxyStatement proxy = (C3P0ProxyStatement) statement;
                try (ResultSet rows = (ResultSet) proxy.rawStatementOperation(
                                Statement.class.getMethod("executeQuery", String.class),
                                C3P0ProxyStatement.RAW_STATEMENT, new Object[]{"SELECT 51"})) {
                    assertTrue(rows.next());
                    assertEquals(51, rows.getInt(1));
                    assertEquals(statement, rows.getStatement());
                }
            }
        } finally {
            pooled.close();
        }
    }
}
