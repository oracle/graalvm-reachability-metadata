/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.Connection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MysqlTestSupport.class)
public class FailoverConnectionProxyTest {
    @Test
    void multiHostJdbcUrlRoutesQueriesAndTransactionSettings() throws Exception {
        try (Connection connection = MysqlTestSupport.connect("failover", Map.of())) {
            connection.setAutoCommit(false);
            assertThat(connection.getAutoCommit()).isFalse();
            MysqlTestSupport.assertQuery(connection, 73);
            connection.commit();
            connection.setAutoCommit(true);
            assertThat(connection.getAutoCommit()).isTrue();
            MysqlTestSupport.assertQuery(connection, 74);
        }
    }
}
