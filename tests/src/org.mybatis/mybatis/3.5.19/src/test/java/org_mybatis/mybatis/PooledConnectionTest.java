/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

public class PooledConnectionTest {
    @Test
    void returnsConnectionsToPoolAndReusesUnderlyingConnection() throws Exception {
        PooledDataSource dataSource = new PooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
        dataSource.setPoolMaximumActiveConnections(1);
        try {
            Connection underlying;
            try (Connection first = dataSource.getConnection()) {
                assertFalse(first.isClosed());
                underlying = PooledDataSource.unwrapConnection(first);
            }
            try (Connection second = dataSource.getConnection();
                    Statement statement = second.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT 9")) {
                assertSame(underlying, PooledDataSource.unwrapConnection(second));
                rows.next();
                assertEquals(9, rows.getInt(1));
            }
        } finally {
            dataSource.forceCloseAll();
        }
    }
}
