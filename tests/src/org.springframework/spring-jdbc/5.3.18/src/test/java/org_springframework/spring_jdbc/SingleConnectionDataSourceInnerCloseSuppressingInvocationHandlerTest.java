/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class SingleConnectionDataSourceInnerCloseSuppressingInvocationHandlerTest {
    @Test
    void delegatesPreparedStatementsAndRollbackToSharedConnection() throws Exception {
        try (Connection physical = JdbcTestSupport.dataSource().getConnection()) {
            SingleConnectionDataSource source = new SingleConnectionDataSource(physical, true);
            try (Connection connection = source.getConnection()) {
                try (Statement schema = connection.createStatement()) {
                    schema.executeUpdate("CREATE TABLE payments (amount INTEGER)");
                }
                connection.setAutoCommit(false);
                assertThat(physical.getAutoCommit()).isFalse();
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO payments VALUES (?)")) {
                    insert.setInt(1, 99);
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                }
                assertThat(JdbcTestSupport.queryInt(connection, "SELECT SUM(amount) FROM payments")).isEqualTo(99);
                connection.rollback();
                assertThat(JdbcTestSupport.queryInt(connection, "SELECT COUNT(*) FROM payments")).isZero();
            } finally {
                source.destroy();
            }
        }
    }
}
