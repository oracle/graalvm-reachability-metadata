/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import org.junit.jupiter.api.Test;

import javax.sql.PooledConnection;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class C3P0PooledConnectionTest {
    @Test
    void traditionalProxyCleansUpStatementsWhenReturned() throws Exception {
        PooledConnection pooled = C3p0Support.wrapper(true).getPooledConnection();
        try {
            Statement statement;
            ResultSet rows;
            try (Connection connection = pooled.getConnection()) {
                statement = connection.createStatement();
                rows = statement.executeQuery("SELECT 46");
                assertTrue(rows.next());
            }
            assertTrue(statement.isClosed());
            assertTrue(rows.isClosed());
            try (Connection connection = pooled.getConnection()) {
                C3p0Support.assertQuery(connection, 47);
            }
        } finally {
            pooled.close();
        }
    }
}
