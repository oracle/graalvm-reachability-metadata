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
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class SingleConnectionDataSourceTest {
    @Test
    void sharesCloseSuppressingConnectionAcrossBorrowers() throws Exception {
        try (Connection physical = JdbcTestSupport.dataSource().getConnection()) {
            SingleConnectionDataSource source = new SingleConnectionDataSource(physical, true);
            try {
                try (Connection first = source.getConnection(); Statement statement = first.createStatement()) {
                    assertThat(first).isInstanceOf(ConnectionProxy.class);
                    assertThat(((ConnectionProxy) first).getTargetConnection()).isSameAs(physical);
                    statement.executeUpdate("CREATE TABLE inventory (quantity INTEGER)");
                    statement.executeUpdate("INSERT INTO inventory VALUES (12)");
                }
                assertThat(physical.isClosed()).isFalse();
                try (Connection second = source.getConnection()) {
                    assertThat(source.shouldClose(second)).isFalse();
                    assertThat(JdbcTestSupport.queryInt(second, "SELECT quantity FROM inventory")).isEqualTo(12);
                }
            } finally {
                source.destroy();
            }
            assertThat(physical.isClosed()).isTrue();
        }
    }
}
