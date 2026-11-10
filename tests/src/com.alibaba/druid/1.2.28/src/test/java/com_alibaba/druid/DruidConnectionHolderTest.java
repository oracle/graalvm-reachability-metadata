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
import oracle.jdbc.OracleDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class DruidConnectionHolderTest {
    @Test
    @Timeout(170)
    void discardsAndReplacesAnOraclePhysicalConnection() throws Exception {
        try (DatabaseContainer database = DatabaseContainer.oracle();
             DruidDataSource dataSource = new DruidDataSource()) {
            dataSource.setDriver(new OracleDriver());
            dataSource.setUrl("jdbc:oracle:thin:@//" + database.address() + "/FREEPDB1");
            dataSource.setUsername("druid");
            dataSource.setPassword(DatabaseContainer.PASSWORD);
            dataSource.setMaxActive(1);
            dataSource.setMaxWait(10000);
            dataSource.setConnectTimeout(10000);
            dataSource.setSocketTimeout(30000);
            dataSource.setValidationQuery("SELECT 1 FROM dual");
            Properties properties = new Properties();
            properties.setProperty("user", "druid");
            properties.setProperty("password", DatabaseContainer.PASSWORD);
            properties.setProperty("oracle.net.CONNECT_TIMEOUT", "10000");
            properties.setProperty("oracle.jdbc.ReadTimeout", "30000");
            try (Connection physical = dataSource.getDriver().connect(dataSource.getUrl(), properties)) {
                DruidConnectionHolder holder = new DruidConnectionHolder(dataSource, physical, 0);
                assertSame(dataSource, holder.getDataSource());
                assertEquals("oracle.jdbc.driver.T4CConnection", physical.getClass().getName());
                assertEquals(89, TestDataSources.queryInt(holder.getConnection(), "SELECT 89 FROM dual"));
                dataSource.discardConnection(holder);
                assertTrue(physical.isClosed());
                assertEquals(1, dataSource.getDiscardCount());
            }
            try (Connection replacement = dataSource.getConnection()) {
                assertEquals(97, TestDataSources.queryInt(replacement, "SELECT 97 FROM dual"));
            }
        }
    }

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
