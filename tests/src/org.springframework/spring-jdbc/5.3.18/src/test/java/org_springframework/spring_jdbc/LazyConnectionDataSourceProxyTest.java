/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jdbc;

import java.sql.Connection;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import static org.assertj.core.api.Assertions.assertThat;

public class LazyConnectionDataSourceProxyTest {
    @Test
    void defersDefaultConnectionUntilAStatementIsNeeded() throws Exception {
        JdbcTestSupport.TrackingDataSource target = new JdbcTestSupport.TrackingDataSource();
        LazyConnectionDataSourceProxy source = lazySource(target);
        try (Connection connection = source.getConnection()) {
            assertThat(connection.getAutoCommit()).isTrue();
            connection.commit();
            connection.rollback();
            assertThat(target.connectionCount()).isZero();
            assertThat(JdbcTestSupport.queryInt(connection, "SELECT 23")).isEqualTo(23);
            assertThat(target.connectionCount()).isEqualTo(1);
        }
        assertThat(target.lastConnection().isClosed()).isTrue();
    }

    @Test
    void defersCredentialedConnectionAndAvoidsUnusedConnections() throws Exception {
        JdbcTestSupport.TrackingDataSource target = new JdbcTestSupport.TrackingDataSource();
        LazyConnectionDataSourceProxy source = lazySource(target);
        try (Connection unused = source.getConnection("sa", "")) {
            unused.setReadOnly(true);
            unused.setAutoCommit(false);
            unused.rollback();
        }
        assertThat(target.connectionCount()).isZero();

        try (Connection connection = source.getConnection("sa", "")) {
            assertThat(target.connectionCount()).isZero();
            assertThat(JdbcTestSupport.queryInt(connection, "SELECT 29")).isEqualTo(29);
            assertThat(target.connectionCount()).isEqualTo(1);
        }
        assertThat(target.lastConnection().isClosed()).isTrue();
    }

    private static LazyConnectionDataSourceProxy lazySource(JdbcTestSupport.TrackingDataSource target) {
        LazyConnectionDataSourceProxy source = new LazyConnectionDataSourceProxy();
        source.setTargetDataSource(target);
        source.setDefaultAutoCommit(true);
        source.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        source.afterPropertiesSet();
        return source;
    }
}
