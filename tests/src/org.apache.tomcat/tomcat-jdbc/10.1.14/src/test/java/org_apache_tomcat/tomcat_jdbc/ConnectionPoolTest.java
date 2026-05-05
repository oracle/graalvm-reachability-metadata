/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_jdbc;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionPoolTest extends JdbcInterceptor {
    private static final AtomicInteger POOL_STARTED_CALLS = new AtomicInteger();
    private static final AtomicInteger POOL_CLOSED_CALLS = new AtomicInteger();

    @Test
    void configuredInterceptorsAreCreatedThroughoutPoolLifecycle() {
        resetLifecycleCounters();

        exerciseConnectionPool(true);
        exerciseConnectionPool(false);

        assertThat(POOL_STARTED_CALLS.get()).isEqualTo(2);
        assertThat(POOL_CLOSED_CALLS.get()).isEqualTo(2);
    }

    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        // This test verifies interceptor lifecycle callbacks that do not require a borrowed connection.
    }

    @Override
    public void poolStarted(ConnectionPool pool) {
        POOL_STARTED_CALLS.incrementAndGet();
    }

    @Override
    public void poolClosed(ConnectionPool pool) {
        POOL_CLOSED_CALLS.incrementAndGet();
    }

    private void exerciseConnectionPool(boolean fairQueue) {
        DataSource dataSource = new DataSource(createPoolProperties(fairQueue));
        ConnectionPool pool = dataSource.getPool();

        assertThat(pool).isNotNull();
        assertThat(pool.isClosed()).isFalse();
        assertThat(pool.getSize()).isZero();

        dataSource.close(true);

        assertThat(pool.isClosed()).isTrue();
        assertThat(dataSource.getPoolSize()).isZero();
    }

    private PoolProperties createPoolProperties(boolean fairQueue) {
        PoolProperties poolProperties = new DirectInterceptorPoolProperties();
        poolProperties.setInitialSize(0);
        poolProperties.setMaxActive(1);
        poolProperties.setJmxEnabled(false);
        poolProperties.setTimeBetweenEvictionRunsMillis(0);
        poolProperties.setFairQueue(fairQueue);
        return poolProperties;
    }

    private void resetLifecycleCounters() {
        POOL_STARTED_CALLS.set(0);
        POOL_CLOSED_CALLS.set(0);
    }

    private static final class DirectInterceptorPoolProperties extends PoolProperties {
        private static final long serialVersionUID = 1L;

        @Override
        public PoolProperties.InterceptorDefinition[] getJdbcInterceptorsAsArray() {
            return new PoolProperties.InterceptorDefinition[] {
                    new PoolProperties.InterceptorDefinition(ConnectionPoolTest.class)
            };
        }
    }
}
