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
public class LoadBalancedConnectionProxyTest {
    @Test
    void loadBalancedConnectionPreservesSessionStateAcrossTransactionBoundaries() throws Exception {
        try (Connection connection = MysqlTestSupport.connect("loadbalance:", Map.of())) {
            connection.setAutoCommit(false);
            MysqlTestSupport.assertQuery(connection, 41);
            connection.commit();
            assertThat(connection.getAutoCommit()).isFalse();
            assertThat(connection.getCatalog()).isEqualTo("test");
            MysqlTestSupport.assertQuery(connection, 42);
            connection.rollback();
            assertThat(connection.isClosed()).isFalse();
        }
    }
}
