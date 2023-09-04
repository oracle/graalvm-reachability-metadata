/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.dbcp2.PoolingDriver;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SuppressWarnings({"SqlNoDataSourceInspection", "deprecation", "SqlDialectInspection"})
public class CommonDbcp2Test {

    /**
     * We cannot write unit tests for the `registerConnectionMBean` property because the version
     * containing <a href="https://issues.apache.org/jira/browse/DBCP-585">Connection level JMX queries result in concurrent access to connection objects, causing errors</a>
     * has not yet been released.
     *
     * @see BasicDataSource
     */
    @Test
    void testBasicDataSource() {
        try (BasicDataSource dataSource = new BasicDataSource()) {
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            dataSource.setUrl("jdbc:h2:mem:test");
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setCacheState(true);
            dataSource.setDefaultQueryTimeout(null);
            dataSource.setEnableAutoCommitOnReturn(true);
            dataSource.setRollbackOnReturn(true);
            dataSource.setInitialSize(0);
            dataSource.setMaxTotal(8);
            dataSource.setMaxIdle(8);
            dataSource.setMinIdle(0);
            dataSource.setMaxWaitMillis(-1);
            dataSource.setTestOnCreate(false);
            dataSource.setTestOnBorrow(true);
            dataSource.setTestOnReturn(false);
            dataSource.setTestWhileIdle(false);
            dataSource.setTimeBetweenEvictionRunsMillis(-1);
            dataSource.setNumTestsPerEvictionRun(3);
            dataSource.setMinEvictableIdleTimeMillis(1000 * 60 * 30);
            dataSource.setSoftMinEvictableIdleTimeMillis(-1);
            dataSource.setMaxConnLifetimeMillis(-1);
            dataSource.setLogExpiredConnections(true);
            dataSource.setConnectionInitSqls(null);
            dataSource.setLifo(true);
            dataSource.setPoolPreparedStatements(false);
            dataSource.setMaxOpenPreparedStatements(-1);
            dataSource.setAccessToUnderlyingConnectionAllowed(false);
            dataSource.setRemoveAbandonedOnMaintenance(false);
            dataSource.setRemoveAbandonedOnBorrow(false);
            dataSource.setRemoveAbandonedTimeout(300);
            dataSource.setLogAbandoned(false);
            dataSource.setAbandonedUsageTracking(false);
            dataSource.setFastFailValidation(false);
            dataSource.setDisconnectionSqlCodes(null);
            assertThat(dataSource.getDefaultAutoCommit()).isNull();
            assertThat(dataSource.getDefaultReadOnly()).isNull();
            assertThat(dataSource.getDefaultTransactionIsolation()).isEqualTo(-1);
            assertThat(dataSource.getDefaultCatalog()).isNull();
            assertThat(dataSource.getValidationQuery()).isNull();
            assertThat(dataSource.getValidationQueryTimeout()).isEqualTo(-1);
            assertDoesNotThrow(() -> {
                try (Connection connection = dataSource.getConnection();
                     Statement statement = connection.createStatement()) {
                    statement.executeQuery("select 1");
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testPoolingDataSource() {
        ConnectionFactory connectionFactory = new DriverManagerConnectionFactory("jdbc:h2:mem:test", null);
        PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null);
        ObjectPool<PoolableConnection> poolableConnectionGenericObjectPool = new GenericObjectPool<>(poolableConnectionFactory);
        poolableConnectionFactory.setPool(poolableConnectionGenericObjectPool);
        PoolingDataSource<PoolableConnection> dataSource = new PoolingDataSource<>(poolableConnectionGenericObjectPool);
        assertThat(dataSource.isAccessToUnderlyingConnectionAllowed()).isEqualTo(false);
        assertDoesNotThrow(() -> {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeQuery("select 1");
            }
        });
    }

    @Test
    void testPoolingDriver() throws ClassNotFoundException, SQLException {
        ConnectionFactory connectionFactory = new DriverManagerConnectionFactory("jdbc:h2:mem:test", null);
        PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null);
        ObjectPool<PoolableConnection> poolableConnectionGenericObjectPool = new GenericObjectPool<>(poolableConnectionFactory);
        poolableConnectionFactory.setPool(poolableConnectionGenericObjectPool);
        Class.forName("org.apache.commons.dbcp2.PoolingDriver");
        PoolingDriver driver = (PoolingDriver) DriverManager.getDriver("jdbc:apache:commons:dbcp:");
        driver.registerPool("testPoolingDriver", poolableConnectionGenericObjectPool);
        assertDoesNotThrow(() -> {
            try (Connection connection = DriverManager.getConnection("jdbc:apache:commons:dbcp:testPoolingDriver");
                 Statement statement = connection.createStatement()) {
                statement.executeQuery("select 1");
            }
        });
    }
}
