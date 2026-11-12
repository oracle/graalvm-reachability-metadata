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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionProxy;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionAwareDataSourceProxyTest {
    @Test
    void enlistsPlainJdbcConnectionsInSpringTransaction() throws Exception {
        JdbcTestSupport.TrackingDataSource target = new JdbcTestSupport.TrackingDataSource();
        TransactionAwareDataSourceProxy source = new TransactionAwareDataSourceProxy(target);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(target));

        try (Connection keeper = target.getConnection(); Statement schema = keeper.createStatement()) {
            schema.executeUpdate("CREATE TABLE messages (id INTEGER PRIMARY KEY)");
            Integer inserted = transaction.execute(status -> {
                try {
                    Connection physical;
                    try (Connection first = source.getConnection(); Statement statement = first.createStatement()) {
                        physical = ((ConnectionProxy) first).getTargetConnection();
                        assertThat(statement.executeUpdate("INSERT INTO messages VALUES (7)")).isEqualTo(1);
                    }
                    assertThat(physical.isClosed()).isFalse();
                    try (Connection second = source.getConnection()) {
                        assertThat(((ConnectionProxy) second).getTargetConnection()).isSameAs(physical);
                        return JdbcTestSupport.queryInt(second, "SELECT id FROM messages");
                    }
                } catch (Exception ex) {
                    throw new AssertionError("Transactional JDBC access failed", ex);
                }
            });
            assertThat(inserted).isEqualTo(7);
            assertThat(new JdbcTemplate(target).queryForObject("SELECT COUNT(*) FROM messages", Integer.class)).isEqualTo(1);
        }
    }
}
