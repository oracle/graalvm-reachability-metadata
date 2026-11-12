/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jdbc;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionAwareDataSourceProxyInnerTransactionAwareInvocationHandlerTest {
    @Test
    void appliesTransactionTimeoutAndRollsBackPlainJdbcWrites() throws Exception {
        DataSource target = JdbcTestSupport.dataSource();
        TransactionAwareDataSourceProxy source = new TransactionAwareDataSourceProxy(target);
        source.setReobtainTransactionalConnections(true);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(target));
        transaction.setTimeout(30);

        try (Connection keeper = target.getConnection(); Statement schema = keeper.createStatement()) {
            schema.executeUpdate("CREATE TABLE reservations (id INTEGER)");
            transaction.executeWithoutResult(status -> {
                try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
                    assertThat(statement.getQueryTimeout()).isBetween(1, 30);
                    assertThat(connection.getAutoCommit()).isFalse();
                    assertThat(statement.executeUpdate("INSERT INTO reservations VALUES (3)")).isEqualTo(1);
                    assertThat(JdbcTestSupport.queryInt(connection, "SELECT COUNT(*) FROM reservations")).isEqualTo(1);
                    status.setRollbackOnly();
                } catch (Exception ex) {
                    throw new AssertionError("Transactional JDBC access failed", ex);
                }
            });
            assertThat(new JdbcTemplate(target).queryForObject("SELECT COUNT(*) FROM reservations", Integer.class)).isZero();
        }
    }
}
