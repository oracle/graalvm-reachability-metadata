/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import com.mysql.cj.jdbc.JdbcConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.sql.PooledConnection;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MysqlTestSupport.class)
public class ConnectionWrapperTest {
    @Test
    void pooledConnectionUnwrapsAndCachesVendorConnectionHandle() throws Exception {
        PooledConnection pool = MysqlTestSupport.pooledConnection();
        try (Connection connection = pool.getConnection()) {
            JdbcConnection vendor = connection.unwrap(JdbcConnection.class);
            assertThat(connection.unwrap(JdbcConnection.class)).isSameAs(vendor);
            vendor.setAutoCommit(false);
            assertThat(vendor.getAutoCommit()).isFalse();
            MysqlTestSupport.assertQuery(vendor, 21);
            vendor.rollback();
        } finally {
            pool.close();
        }
    }
}
