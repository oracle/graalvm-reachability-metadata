/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jdbc;

import java.sql.Connection;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.ConnectionProxy;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import static org.assertj.core.api.Assertions.assertThat;

public class LazyConnectionDataSourceProxyInnerLazyConnectionInvocationHandlerTest {
    @Test
    void appliesDeferredSettingsAndDelegatesTransactionOperations() throws Exception {
        LazyConnectionDataSourceProxy source = new LazyConnectionDataSourceProxy(JdbcTestSupport.dataSource());
        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            try (Statement statement = connection.createStatement()) {
                Connection physical = ((ConnectionProxy) connection).getTargetConnection();
                assertThat(physical.getAutoCommit()).isFalse();
                assertThat(physical.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_SERIALIZABLE);
                statement.executeUpdate("CREATE TABLE ledger (id INTEGER PRIMARY KEY)");
                statement.executeUpdate("INSERT INTO ledger VALUES (1)");
                connection.commit();
                statement.executeUpdate("INSERT INTO ledger VALUES (2)");
                connection.rollback();
                assertThat(JdbcTestSupport.queryInt(connection, "SELECT COUNT(*) FROM ledger")).isEqualTo(1);
            }
        }
    }
}
