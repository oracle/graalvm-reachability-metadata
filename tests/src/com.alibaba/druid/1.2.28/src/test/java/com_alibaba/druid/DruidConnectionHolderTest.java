/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.pool.DruidConnectionHolder;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class DruidConnectionHolderTest {
    @Test
    void restoresConnectionStateWhenReturningToThePool() throws Exception {
        try (DruidDataSource dataSource = TestDataSources.create("holder")) {
            DruidConnectionHolder holder;
            try (DruidPooledConnection connection = dataSource.getConnection()) {
                holder = connection.getConnectionHolder();
                assertSame(dataSource, holder.getDataSource());
                assertEquals(Connection.TRANSACTION_READ_COMMITTED, holder.getUnderlyingTransactionIsolation());
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                assertEquals(61, TestDataSources.queryInt(connection, "SELECT 61"));
                connection.rollback();
            }
            try (DruidPooledConnection connection = dataSource.getConnection()) {
                assertSame(holder, connection.getConnectionHolder());
                assertTrue(connection.getAutoCommit());
                assertEquals(Connection.TRANSACTION_READ_COMMITTED, connection.getTransactionIsolation());
                assertEquals(67, TestDataSources.queryInt(connection, "SELECT 67"));
            }
        }
    }
}
