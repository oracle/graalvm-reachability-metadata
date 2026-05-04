/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_jdbc;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.interceptor.ConnectionState;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConnectionPoolTest {

    @Test
    void initializesInterceptorChainAndNotifiesItWhenPoolCloses() {
        DataSource dataSource = new DataSource(createDriverPoolProperties("connection_pool_lifecycle"));

        try {
            assertThat(dataSource.getPool()).isNotNull();
            assertThat(dataSource.getPoolSize()).isZero();
        } finally {
            dataSource.close();
        }

        assertThat(dataSource.getPoolSize()).isZero();
    }

    @Test
    void reportsInvalidJdbcProxyInterfaceWhenBorrowingPooledConnection() {
        DataSource dataSource = new DataSource(createDriverPoolProperties("connection_pool_jdbc_proxy"));

        try {
            assertThatThrownBy(dataSource::getConnection)
                    .isInstanceOf(SQLException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("org.apache.tomcat.jdbc.pool.PooledConnection is not an interface");
        } finally {
            dataSource.close(true);
        }
    }

    @Test
    void reportsInvalidXaProxyInterfaceWhenUnderlyingDataSourceSupportsXa() {
        DataSource dataSource = new DataSource(createXaPoolProperties("connection_pool_xa_proxy"));

        try {
            assertThatThrownBy(dataSource::getXAConnection)
                    .isInstanceOf(SQLException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("org.apache.tomcat.jdbc.pool.PooledConnection is not an interface");
        } finally {
            dataSource.close(true);
        }
    }

    private PoolProperties createDriverPoolProperties(String databaseName) {
        PoolProperties poolProperties = new PoolProperties();
        poolProperties.setDriverClassName("org.h2.Driver");
        poolProperties.setUrl("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        poolProperties.setUsername("sa");
        poolProperties.setPassword("");
        configureCommonPoolProperties(poolProperties);
        return poolProperties;
    }

    private PoolProperties createXaPoolProperties(String databaseName) {
        JdbcDataSource h2DataSource = new JdbcDataSource();
        h2DataSource.setURL("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        h2DataSource.setUser("sa");
        h2DataSource.setPassword("");

        PoolProperties poolProperties = new PoolProperties();
        poolProperties.setDataSource(h2DataSource);
        poolProperties.setUsername("sa");
        poolProperties.setPassword("");
        configureCommonPoolProperties(poolProperties);
        return poolProperties;
    }

    private void configureCommonPoolProperties(PoolProperties poolProperties) {
        poolProperties.setInitialSize(0);
        poolProperties.setMinIdle(0);
        poolProperties.setMaxIdle(1);
        poolProperties.setMaxActive(1);
        poolProperties.setJmxEnabled(false);
        poolProperties.setTimeBetweenEvictionRunsMillis(0);
        poolProperties.setJdbcInterceptors(ConnectionState.class.getName());
    }
}
